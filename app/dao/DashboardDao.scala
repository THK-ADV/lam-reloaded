package dao

import java.util.UUID

import dao.helper.Core
import database.helper.{EmployeeStatus, StudentStatus}
import javax.inject.Inject
import models.{AuthorityAtom, Dashboard, EmployeeDashboard, ReportCardEntryType, ReportCardEvaluationAtom, Semester, StudentDashboard, StudentLike, User}
import org.joda.time.LocalDate
import slick.jdbc.PostgresProfile.api._
import utils.LwmDateTime._

import scala.concurrent.{ExecutionContext, Future}

trait DashboardDao extends Core {

  def dashboard(systemId: String)(atomic: Boolean, numberOfUpcomingElements: Option[Int], entriesSinceNow: Boolean, sortedByDate: Boolean): Future[Dashboard] = {
    for {
      maybeSemester <- semesterDao.getSingle(UUID.fromString("0496eec7-ad06-4d82-86e1-ad7782dcd92b")) // semesterDao.current(atomic)
      semester <- maybeSemester.fold(throw new Throwable(s"none or more than one semester was found, but there should only one current semester"))(Future.successful)
      user <- userDao.getSingleWhere(_.systemId === systemId, atomic = atomic)
      board <- user match {
        case Some(student: StudentLike) =>
          studentDashboard(student, semester)(atomic, numberOfUpcomingElements, entriesSinceNow, sortedByDate)
        case Some(employee) =>
          employeeDashboard(employee, semester)(atomic, numberOfUpcomingElements, entriesSinceNow, sortedByDate)
        case None =>
          throw new Throwable(s"no user found for $systemId")
      }
    } yield board
  }

  private def studentDashboard(student: StudentLike, semester: Semester)(atomic: Boolean, numberOfUpcomingElements: Option[Int], entriesSinceNow: Boolean, sortedByDate: Boolean) = {
    for {
      labworks <- labworkDao get(List(LabworkSemesterFilter(semester.id.toString), LabworkDegreeFilter(student.enrollmentId.toString)), atomic = atomic)
      labworkIds = labworks map (_.id)

      lappQuery = labworkApplicationDao filterValidOnly (app => app.labwork.inSet(labworkIds) && app.applicant === student.id)
      lapps <- labworkApplicationDao getByQuery(lappQuery, atomic = atomic)

      allCardsQuery = reportCardEntryDao filterValidOnly (e => e.labwork.inSet(labworkIds) && e.student === student.id)
      sinceNowFilter = if (entriesSinceNow) Option apply ReportCardEntrySinceFilter(LocalDate.now.stringMillis).predicate else Option.empty
      cardsSinceNowQuery = sinceNowFilter.fold(allCardsQuery)(f => allCardsQuery.filter(f.apply))
      cards <- reportCardEntryDao getByQuery(cardsSinceNowQuery, atomic = atomic)
      upcomingCards = numberOfUpcomingElements.fold(cards)(n => cards.groupBy(_.labworkId).flatMap(_._2.take(n)).toSeq)
      sortedUpcomingCards = if (sortedByDate) upcomingCards sortBy (s => (s.date, s.start)) else upcomingCards

      allEvals <- reportCardEvaluationDao get(List(StudentFilter(student.id.toString)), atomic = true) // must be atomic
      passedEvals = allEvals map (_.asInstanceOf[ReportCardEvaluationAtom]) groupBy (_.labwork) map {
        case (labwork, evals) =>
          val boolBased = evals filter (e => ReportCardEntryType.BooleanBasedTypes.exists(_.entryType == e.label))
          val intBased = evals filter (e => ReportCardEntryType.IntBasedTypes.exists(_.entryType == e.label))

          val passed = boolBased map (_.bool) reduceOption (_ && _) getOrElse false
          val bonus = intBased map (_.int) sum

          (labwork.course.abbreviation, labwork.semester.abbreviation, passed, bonus)
      }

      groupsQuery = groupDao filterValidOnly (g => g.labwork.inSet(labworkIds) && g.contains(student.id)) map (g => (g.label, g.labwork))
      groupLabels <- db run groupsQuery.result
      groups = groupLabels.map(g => (g._1, labworks.find(_.id == g._2).get))

    } yield StudentDashboard(student, StudentStatus, semester, labworks, lapps, groups, sortedUpcomingCards, allEvals, passedEvals toSeq)
  }

  private def employeeDashboard(employee: User, semester: Semester)(atomic: Boolean, numberOfUpcomingElements: Option[Int], entriesSinceNow: Boolean, sortedByDate: Boolean) = {
    for {
      authorities <- authorityDao get List(AuthorityUserFilter(employee.id.toString)) // must be atomic ...
      courses = authorities flatMap (_.asInstanceOf[AuthorityAtom].course) // in order to get his/her courses
      courseIds = courses map (_.id)

      inSemesterAndCourse = scheduleEntryDao filterValidOnly (q => q.labworkFk.filter(_.semester === semester.id).exists && q.memberOfCourses(courseIds))
      sinceNowFilter = if (entriesSinceNow) Option apply ScheduleEntrySinceFilter(LocalDate.now.stringMillis).predicate else Option.empty
      isSemesterAndCourseAndSinceNow = sinceNowFilter.fold(inSemesterAndCourse)(f => inSemesterAndCourse.filter(f.apply))

      scheduleEntries <- scheduleEntryDao getByQuery(isSemesterAndCourseAndSinceNow, atomic = atomic)
      upcomingEntries = numberOfUpcomingElements.fold(scheduleEntries)(n => scheduleEntries.groupBy(_.labworkId).flatMap(_._2.take(n)).toSeq)
      sortedUpcomingEntries = if (sortedByDate) upcomingEntries sortBy (s => (s.date, s.start)) else upcomingEntries

    } yield EmployeeDashboard(employee, EmployeeStatus, semester, courses, sortedUpcomingEntries)
  }

  protected def userDao: UserDao

  protected def semesterDao: SemesterDao

  protected def labworkDao: LabworkDao

  protected def labworkApplicationDao: LabworkApplicationDao

  protected def reportCardEntryDao: ReportCardEntryDao

  protected def reportCardEvaluationDao: ReportCardEvaluationDao

  protected def groupDao: GroupDao

  protected def authorityDao: AuthorityDao

  protected def scheduleEntryDao: ScheduleEntryDao
}

final class DashboardDaoImpl @Inject()(
  val db: Database,
  val userDao: UserDao,
  val semesterDao: SemesterDao,
  val labworkDao: LabworkDao,
  val labworkApplicationDao: LabworkApplicationDao,
  val reportCardEntryDao: ReportCardEntryDao,
  val reportCardEvaluationDao: ReportCardEvaluationDao,
  val groupDao: GroupDao,
  val authorityDao: AuthorityDao,
  val scheduleEntryDao: ScheduleEntryDao,
  val executionContext: ExecutionContext
) extends DashboardDao