package bind.labwork

import java.util.UUID

import base.SesameDbSpec
import models._
import models.labwork._
import models.users.User
import org.joda.time.{LocalDate, LocalTime}
import org.w3.banana.PointedGraph
import org.w3.banana.sesame.Sesame
import store.bind.Bindings

import scala.util.{Failure, Success}

class ReportCardBindingSpec extends SesameDbSpec {

  import ops._

  val bindings = Bindings[Sesame](namespace)
  import bindings.ReportCardEntryBinding.reportCardEntryBinder
  import bindings.ReportCardEntryTypeBinding.reportCardEntryTypeBinding
  import bindings.{localDateBinder, localTimeBinder, uuidBinder, uuidRefBinder}

  val entries = (0 until 5).map { n =>
    ReportCardEntry(UUID.randomUUID, UUID.randomUUID, n.toString, LocalDate.now.plusWeeks(n), LocalTime.now.plusHours(n), LocalTime.now.plusHours(n + 1), UUID.randomUUID(), ReportCardEntryType.all)
  }.toSet
  val reportCardEntry = {
    val first = entries.head
    val rescheduled = Rescheduled(LocalDate.now, LocalTime.now, LocalTime.now, UUID.randomUUID)
    ReportCardEntry(first.student, first.labwork, first.label, first.date, first.start, first.end, first.room, first.entryTypes, Some(rescheduled), first.id)
  }
  val entryType = ReportCardEntryType.Attendance

  val entryGraph = URI(ReportCardEntry.generateUri(reportCardEntry)).a(lwm.ReportCardEntry)
    .--(lwm.student).->-(reportCardEntry.student)(ops, uuidRefBinder(User.splitter))
    .--(lwm.labwork).->-(reportCardEntry.labwork)(ops, uuidRefBinder(Labwork.splitter))
    .--(lwm.label).->-(reportCardEntry.label)
    .--(lwm.date).->-(reportCardEntry.date)
    .--(lwm.start).->-(reportCardEntry.start)
    .--(lwm.end).->-(reportCardEntry.end)
    .--(lwm.room).->-(reportCardEntry.room)(ops, uuidRefBinder(Room.splitter))
    .--(lwm.entryTypes).->-(reportCardEntry.entryTypes)
    .--(lwm.rescheduled).->-(reportCardEntry.entryTypes)
    .--(lwm.id).->-(reportCardEntry.id).graph

  val typeGraph = (URI(ReportCardEntryType.generateUri(entryType)).a(lwm.ReportCardEntryType)
    -- lwm.entryType ->- entryType.entryType
    -- lwm.bool ->- entryType.bool
    -- lwm.int ->- entryType.int
    -- lwm.id ->- entryType.id
  ).graph

  "A ReportCardBindingSpec " should {

    "successfully serialise a report card entry" in {
      val entry = reportCardEntryBinder.fromPG(reportCardEntry.toPG)

      entry shouldBe Success(reportCardEntry)
    }

    "return a RDF graph representation of a report card entry type" in {
      val graph = entryType.toPG.graph

      graph isIsomorphicWith typeGraph shouldBe true
    }

    "return a report card entry type based on a RDF graph representation" in {
      val expectedType = PointedGraph[Rdf](URI(ReportCardEntryType.generateUri(entryType)), typeGraph).as[ReportCardEntryType]

      expectedType match {
        case Success(s) =>
          s shouldEqual entryType
        case Failure(e) =>
          fail(s"Unable to deserialise report card entry type graph: $e")
      }
    }
  }
}