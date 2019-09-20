package dao

import dao.helper.TableFilter
import database._
import javax.inject.Inject
import models._
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import utils.date.DateTimeOps.SqlTimestampConverter

import scala.concurrent.{ExecutionContext, Future}

//case class StudentFilter(value: String) extends TableFilter[ReportCardEvaluationTable] { // TODO rethink
//  override def predicate = _.student === UUID.fromString(value)
//}
//
//case class LabworkFilter(value: String) extends TableFilter[ReportCardEvaluationTable] {
//  override def predicate = _.labwork === UUID.fromString(value)
//}
//
//case class CourseFilter(value: String) extends TableFilter[ReportCardEvaluationTable] {
//  override def predicate = _.memberOfCourse(value)
//}
//
//case class LabelFilter(value: String) extends TableFilter[ReportCardEvaluationTable] {
//  override def predicate = _.label === value
//}
//
//case class BoolFilter(value: String) extends TableFilter[ReportCardEvaluationTable] {
//  override def predicate = _.bool === Try(value.toBoolean).getOrElse(false)
//}
//
//case class IntFilter(value: String) extends TableFilter[ReportCardEvaluationTable] {
//  override def predicate = _.int === Try(value.toInt).getOrElse(-1)
//}
//
//case class MinIntFilter(value: String) extends TableFilter[ReportCardEvaluationTable] {
//  override def predicate = _.int >= Try(value.toInt).getOrElse(-1)
//}
//
//case class MaxIntFilter(value: String) extends TableFilter[ReportCardEvaluationTable] {
//  override def predicate = _.int <= Try(value.toInt).getOrElse(-1)
//}

trait ReportCardEvaluationDao extends AbstractDao[ReportCardEvaluationTable, ReportCardEvaluationDb, ReportCardEvaluationLike] {

  import TableFilter.{labelFilterEquals, labworkFilter, userFilter}

  override val tableQuery = TableQuery[ReportCardEvaluationTable]

  override protected def toAtomic(query: Query[ReportCardEvaluationTable, ReportCardEvaluationDb, Seq]): Future[Seq[ReportCardEvaluationLike]] = {
    val mandatory = for {
      q <- query
      labwork <- q.labworkFk
      student <- q.userFk
      course <- labwork.courseFk
      degree <- labwork.degreeFk
      semester <- labwork.semesterFk
      lecturer <- course.userFk
    } yield (q, labwork, student, course, degree, semester, lecturer)

    db.run(mandatory.result.map(_.map {
      case (e, l, u, c, d, s, lec) =>
        val labworkAtom = {
          val courseAtom = CourseAtom(c.label, c.description, c.abbreviation, lec.toUniqueEntity, c.semesterIndex, c.id)
          LabworkAtom(l.label, l.description, s.toUniqueEntity, courseAtom, d.toUniqueEntity, l.subscribable, l.published, l.id)
        }

        ReportCardEvaluationAtom(u.toUniqueEntity, labworkAtom, e.label, e.bool, e.int, e.lastModified.dateTime, e.id)
    }))
  }

  override protected def toUniqueEntity(query: Query[ReportCardEvaluationTable, ReportCardEvaluationDb, Seq]): Future[Seq[ReportCardEvaluationLike]] = {
    db.run(query.result.map(_.map(e => ReportCardEvaluation(e.student, e.labwork, e.label, e.bool, e.int, e.lastModified.dateTime, e.id))))
  }

  override protected def existsQuery(entity: ReportCardEvaluationDb): Query[ReportCardEvaluationTable, ReportCardEvaluationDb, Seq] = {
    filterBy(List(userFilter(entity.student), labworkFilter(entity.labwork), labelFilterEquals(entity.label)))
  }

  override protected def shouldUpdate(existing: ReportCardEvaluationDb, toUpdate: ReportCardEvaluationDb): Boolean = {
    existing.student == toUpdate.student &&
      existing.labwork == toUpdate.labwork &&
      existing.label == toUpdate.label
  }
}

final class ReportCardEvaluationDaoImpl @Inject()(val db: Database, val executionContext: ExecutionContext) extends ReportCardEvaluationDao
