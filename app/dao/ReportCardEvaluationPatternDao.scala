package dao

import java.util.UUID

import javax.inject.Inject
import models.ReportCardEvaluationPattern
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import database.{ReportCardEvaluationPatternDb, ReportCardEvaluationPatternTable, TableFilter}

case class EvaluationPatternPropertyFilter(value: String) extends TableFilter[ReportCardEvaluationPatternTable] {
  override def predicate = _.property.toLowerCase === value.toLowerCase
}

case class EvaluationPatternEntryTypeFilter(value: String) extends TableFilter[ReportCardEvaluationPatternTable] {
  override def predicate = _.entryType.toLowerCase === value.toLowerCase
}

case class EvaluationPatternLabworkFilter(value: String) extends TableFilter[ReportCardEvaluationPatternTable] {
  override def predicate = _.labwork === UUID.fromString(value)
}

case class EvaluationPatternCourseFilter(value: String) extends TableFilter[ReportCardEvaluationPatternTable] {
  override def predicate = _.memberOfCourse(value)
}

trait ReportCardEvaluationPatternDao extends AbstractDao[ReportCardEvaluationPatternTable, ReportCardEvaluationPatternDb, ReportCardEvaluationPattern] {

  import scala.concurrent.ExecutionContext.Implicits.global

  override val tableQuery = TableQuery[ReportCardEvaluationPatternTable]

  override protected def toAtomic(query: Query[ReportCardEvaluationPatternTable, ReportCardEvaluationPatternDb, Seq]) = toUniqueEntity(query)

  override protected def toUniqueEntity(query: Query[ReportCardEvaluationPatternTable, ReportCardEvaluationPatternDb, Seq]) = {
    db.run(query.result.map(_.map(_.toUniqueEntity)))
  }

  override protected def existsQuery(entity: ReportCardEvaluationPatternDb): Query[ReportCardEvaluationPatternTable, ReportCardEvaluationPatternDb, Seq] = {
    filterBy(List(
      EvaluationPatternPropertyFilter(entity.property),
      EvaluationPatternEntryTypeFilter(entity.entryType),
      EvaluationPatternLabworkFilter(entity.labwork.toString)
    ))
  }

  override protected def shouldUpdate(existing: ReportCardEvaluationPatternDb, toUpdate: ReportCardEvaluationPatternDb): Boolean = {
    (existing.min != toUpdate.min) &&
      (existing.property == toUpdate.property && existing.entryType == toUpdate.entryType && existing.labwork == toUpdate.labwork)
  }
}

final class ReportCardEvaluationPatternDaoImpl @Inject()(val db: PostgresProfile.backend.Database) extends ReportCardEvaluationPatternDao