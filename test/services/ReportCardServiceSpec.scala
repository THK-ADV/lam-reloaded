package services

import java.util.UUID

import base.TestBaseDefinition
import models.ReportCardEntryType._
import models._
import org.joda.time.{LocalDate, LocalTime}
import org.scalatest.WordSpec
import scala.util.{Failure, Success}

object ReportCardServiceSpec {

  def integer(assEntry: AssignmentEntry, appEntry: ScheduleEntryG, cEntry: ReportCardEntry): Boolean = {
    def integerTypes(left: Set[AssignmentEntryType], right: Set[ReportCardEntryType]): Boolean = {
      def toAssignmentEntryType(cardEntry: ReportCardEntryType): AssignmentEntryType = {
        AssignmentEntryType(cardEntry.entryType, cardEntry.bool, cardEntry.int)
      }

      left == right.map(toAssignmentEntryType)
    }

    assEntry.index == cEntry.index &&
      assEntry.label == cEntry.label &&
      integerTypes(assEntry.types, cEntry.entryTypes) &&
      appEntry.date.isEqual(cEntry.date) &&
      appEntry.start.isEqual(cEntry.start) &&
      appEntry.room == cEntry.room
  }

  def group(students: Int): Group = {
    val members = (0 until students).map(_ => UUID.randomUUID()).toSet
    Group("", UUID.randomUUID(), members)
  }

  def plan(amount: Int): AssignmentPlan = {
    def randomTypes: Set[AssignmentEntryType] = {
      import scala.util.Random._

      val types = AssignmentEntryType.all.toVector
      shuffle(types).take(nextInt(types.size)).toSet
    }

    val pe = (0 until amount).map(n => AssignmentEntry(n, n.toString, randomTypes)).toSet
    AssignmentPlan(UUID.randomUUID(), amount, amount, pe)
  }

  def schedule(amount: Int, aps: Int, emptyMembers: Boolean = false): ScheduleG = {
    val se = (0 until amount).map { n =>
      val start = LocalTime.now.plusHours(n)
      val grp = if (emptyMembers) group(0) else group(20)

      ScheduleEntryG(start, start.plusHours(n), LocalDate.now.plusWeeks(n), UUID.randomUUID(), UUID.randomUUID(), grp)
    }.toVector

    val see = (0 until aps).foldLeft(Vector.empty[ScheduleEntryG]) { (vec, _) =>
      vec ++ se
    }

    ScheduleG(UUID.randomUUID(), see, UUID.randomUUID())
  }
}

class ReportCardServiceSpec extends WordSpec with TestBaseDefinition {

  val reportCardService = new ReportCardService

  val planEntries = {
    import AssignmentEntryType._

    Vector(
      AssignmentEntry(0, "Einführung", Set(Attendance)),
      AssignmentEntry(1, "Liveaufgabe 1 - C", Set(Attendance, Certificate)),
      AssignmentEntry(2, "Liveaufgabe 2 - C", Set(Attendance, Certificate)),
      AssignmentEntry(3, "Ilias Test", Set(Attendance, Certificate, Bonus)),
      AssignmentEntry(4, "Liveaufgabe 3 - Java", Set(Attendance, Certificate)),
      AssignmentEntry(5, "Liveaufgabe 4 - Java", Set(Attendance, Certificate)),
      AssignmentEntry(6, "Codereview", Set(Attendance, Certificate, Supplement)),
      AssignmentEntry(7, "Codereview", Set(Attendance, Certificate, Supplement))
    )
  }

  "A ReportCardServiceSpec " should {

    "successfully return report cards for given schedule" in {
      import ReportCardServiceSpec._
      import models.schedule.TimetableDateEntry._

      val amount = 8
      val assignmentPlan = plan(amount)
      val scheduleG = schedule(amount, assignmentPlan.entries.size)

      val result = reportCardService.reportCards(scheduleG, assignmentPlan)

      result match {
        case Success(cards) =>
          cards.nonEmpty shouldBe true
          cards.forall(_.entries.size == assignmentPlan.entries.size) shouldBe true
          cards.forall(c => c.entries.size == scheduleG.entries.count(_.group.members.contains(c.student))) shouldBe true

          cards.forall( c =>
            c.entries.flatMap(_.entryTypes.map(_.id)).size == assignmentPlan.entries.toVector.flatMap(_.types).size
          ) shouldBe true

          cards.forall { c =>
            val assignments = assignmentPlan.entries.toVector.sortBy(_.index)
            val appointments = scheduleG.entries.filter(_.group.members.contains(c.student)).sortBy(toLocalDateTime)
            val studentApps = c.entries.toVector.sortBy(_.index)

            (assignments, appointments, studentApps).zipped.forall {
              case (ass, app, s) => integer(ass, app, s)
            }
          } shouldBe true
        case Failure(e) =>
          fail("error while creating report cards", e)
      }
    }

    "fail when there are no students" in {
      import ReportCardServiceSpec._

      val amount = 8
      val assignmentPlan = plan(amount)
      val scheduleG = schedule(amount, assignmentPlan.entries.size, emptyMembers = true)
      val expectedError = new Throwable(s"No students found while creating report cards for ${scheduleG.id}")

      val result = reportCardService.reportCards(scheduleG, assignmentPlan)

      result match {
        case Failure(e) =>
          e.getMessage shouldBe expectedError.getMessage
        case _ =>
          fail("report cards should be empty when there are no students")
      }
    }

    "pass a student's report card when everything is fine" in {
      val bonusPoints = 10
      val cardEntries = planEntries.map { e =>
        val types = e.types.map {
          case att if att.entryType == Attendance.entryType => ReportCardEntryType(att.entryType, !(e.index == 0), 0)
          case cert if cert.entryType == Certificate.entryType => ReportCardEntryType(cert.entryType, bool = true, 0)
          case bonus if bonus.entryType == Bonus.entryType => ReportCardEntryType(bonus.entryType, bool = false, bonusPoints)
          case supp if supp.entryType == Supplement.entryType => ReportCardEntryType(supp.entryType, bool = true, 0)
        }

        ReportCardEntry(e.index, e.label, LocalDate.now, LocalTime.now, LocalTime.now, UUID.randomUUID(), types)
      }
      val types = planEntries.flatMap(_.types)
      val attendance = types.count(_.entryType == Attendance.entryType)
      val mandatory = types.count(_.entryType == Certificate.entryType)

      val assignmentPlan = AssignmentPlan(UUID.randomUUID(), attendance - 1, mandatory, planEntries.toSet)
      val reportCard = ReportCard(UUID.randomUUID(), UUID.randomUUID(), cardEntries.toSet)

      val result = reportCardService.evaluate(assignmentPlan, reportCard)

      result.size shouldBe ReportCardEntryType.all.size
      result.forall(_.bool)
      result.forall(_.int == bonusPoints)
    }

    "pass a student's report card even when he barley performed" in {
      val cardEntries = planEntries.map { e =>
        val types = e.types.map {
          case att if att.entryType == Attendance.entryType => ReportCardEntryType(att.entryType, !(e.index == 0 || e.index == 1), 0)
          case cert if cert.entryType == Certificate.entryType => ReportCardEntryType(cert.entryType, !(e.index == 1 || e.index == 2 || e.index == 7), 0)
          case bonus if bonus.entryType == Bonus.entryType => ReportCardEntryType(bonus.entryType, bool = false, 0)
          case supp if supp.entryType == Supplement.entryType => ReportCardEntryType(supp.entryType, bool = true, 0)
        }

        ReportCardEntry(e.index, e.label, LocalDate.now, LocalTime.now, LocalTime.now, UUID.randomUUID(), types)
      }
      val types = planEntries.flatMap(_.types)
      val attendance = types.count(_.entryType == Attendance.entryType)
      val mandatory = types.count(_.entryType == Certificate.entryType)

      val assignmentPlan = AssignmentPlan(UUID.randomUUID(), attendance - 2, mandatory - 3, planEntries.toSet)
      val reportCard = ReportCard(UUID.randomUUID(), UUID.randomUUID(), cardEntries.toSet)

      val result = reportCardService.evaluate(assignmentPlan, reportCard)

      result.size shouldBe ReportCardEntryType.all.size
      result.foreach {
        case att if att.label == Attendance.entryType => att.bool shouldBe true
        case cert if cert.label == Certificate.entryType => cert.bool shouldBe true
        case bonus if bonus.label == Bonus.entryType => bonus.int shouldBe 0
        case supp if supp.label == Supplement.entryType => supp.bool shouldBe true
      }
    }

    "deny a student's report card when some mandatory certificates are missing" in {
      val bonusPoints = 5
      val cardEntries = planEntries.map { e =>
        val types = e.types.map {
          case att if att.entryType == Attendance.entryType => ReportCardEntryType(att.entryType, bool = true, 0)
          case cert if cert.entryType == Certificate.entryType => ReportCardEntryType(cert.entryType, !(e.index == 2 || e.index == 5), 0)
          case bonus if bonus.entryType == Bonus.entryType => ReportCardEntryType(bonus.entryType, bool = false, bonusPoints)
          case supp if supp.entryType == Supplement.entryType => ReportCardEntryType(supp.entryType, bool = true, 0)
        }

        ReportCardEntry(e.index, e.label, LocalDate.now, LocalTime.now, LocalTime.now, UUID.randomUUID(), types)
      }
      val types = planEntries.flatMap(_.types.toVector)
      val attendance = types.count(_.entryType == Attendance.entryType)
      val mandatory = types.count(_.entryType == Certificate.entryType)

      val assignmentPlan = AssignmentPlan(UUID.randomUUID(), attendance - 1, mandatory, planEntries.toSet)
      val reportCard = ReportCard(UUID.randomUUID(), UUID.randomUUID(), cardEntries.toSet)

      val result = reportCardService.evaluate(assignmentPlan, reportCard)

      result.size shouldBe ReportCardEntryType.all.size
      result.foreach {
        case att if att.label == Attendance.entryType => att.bool shouldBe true
        case cert if cert.label == Certificate.entryType => cert.bool shouldBe false
        case bonus if bonus.label == Bonus.entryType => bonus.int shouldBe bonusPoints
        case supp if supp.label == Supplement.entryType => supp.bool shouldBe true
      }
    }

    "deny a student's report card when a supplement is missing" in {
      val cardEntries = planEntries.map { e =>
        val types = e.types.map {
          case att if att.entryType == Attendance.entryType => ReportCardEntryType(att.entryType, bool = true, 0)
          case cert if cert.entryType == Certificate.entryType => ReportCardEntryType(cert.entryType, bool = true, 0)
          case bonus if bonus.entryType == Bonus.entryType => ReportCardEntryType(bonus.entryType, bool = false, 0)
          case supp if supp.entryType == Supplement.entryType => ReportCardEntryType(supp.entryType, !(e.index == 7), 0)
        }

        ReportCardEntry(e.index, e.label, LocalDate.now, LocalTime.now, LocalTime.now, UUID.randomUUID(), types)
      }
      val types = planEntries.flatMap(_.types)
      val attendance = types.count(_.entryType == Attendance.entryType)
      val mandatory = types.count(_.entryType == Certificate.entryType)

      val assignmentPlan = AssignmentPlan(UUID.randomUUID(), attendance - 1, mandatory, planEntries.toSet)
      val reportCard = ReportCard(UUID.randomUUID(), UUID.randomUUID(), cardEntries.toSet)

      val result = reportCardService.evaluate(assignmentPlan, reportCard)

      result.size shouldBe ReportCardEntryType.all.size
      result.foreach {
        case att if att.label == Attendance.entryType => att.bool shouldBe true
        case cert if cert.label == Certificate.entryType => cert.bool shouldBe true
        case bonus if bonus.label == Bonus.entryType => bonus.int shouldBe 0
        case supp if supp.label == Supplement.entryType => supp.bool shouldBe false
      }
    }
  }
}
