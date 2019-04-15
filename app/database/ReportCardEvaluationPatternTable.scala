package database

import java.sql.Timestamp
import java.util.UUID

import models.helper.EvaluationProperty
import models.{ReportCardEvaluationPattern, UniqueDbEntity}
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api._
import utils.date.DateTimeOps.DateTimeConverter

class ReportCardEvaluationPatternTable(tag: Tag) extends Table[ReportCardEvaluationPatternDb](tag, "REPORT_CARD_EVALUATION_PATTERN") with UniqueTable with LabworkIdTable with EntryTypeTable {
  def min = column[Int]("MIN")

  def property = column[String]("property")

  override def * = (labwork, entryType, min, property, lastModified, invalidated, id) <> ((ReportCardEvaluationPatternDb.apply _).tupled, ReportCardEvaluationPatternDb.unapply)
}

case class ReportCardEvaluationPatternDb(labwork: UUID, entryType: String, min: Int, property: String, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {
  override def toUniqueEntity = ReportCardEvaluationPattern(labwork, entryType, min, EvaluationProperty(property), id)
}