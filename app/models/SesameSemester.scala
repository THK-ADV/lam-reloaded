package models

import java.sql.{Date, Timestamp}
import java.util.UUID

import org.joda.time.{DateTime, Interval, LocalDate}
import play.api.libs.json.{Json, Reads, Writes}
import utils.LwmDateTime._

case class SesameSemester(label: String, abbreviation: String, start: LocalDate, end: LocalDate, examStart: LocalDate, invalidated: Option[DateTime] = None, id: UUID = SesameSemester.randomUUID) extends UniqueEntity {

  override def equals(that: scala.Any): Boolean = that match {
    case SesameSemester(l, a, s, e, ex, _, i) =>
      l == label &&
        a == abbreviation &&
        s.isEqual(start) &&
        e.isEqual(end) &&
        ex.isEqual(examStart) &&
        i == id
    case _ => false
  }
}

case class SemesterProtocol(label: String, abbreviation: String, start: LocalDate, end: LocalDate, examStart: LocalDate)

object SesameSemester extends UriGenerator[SesameSemester] {
  override def base: String = "semesters"

  def isCurrent(semester: SesameSemester): Boolean = {
    new Interval(semester.start.toDateTimeAtCurrentTime, semester.end.toDateTimeAtCurrentTime).containsNow
  }
}

// Postgres

case class PostgresSemester(label: String, abbreviation: String, start: LocalDate, end: LocalDate, examStart: LocalDate, id: UUID = SesameSemester.randomUUID) extends UniqueEntity

case class SemesterDb(label: String, abbreviation: String, start: Date, end: Date, examStart: Date, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = SesameSemester.randomUUID) extends UniqueDbEntity {

  import utils.LwmDateTime._

  override def toLwmModel = PostgresSemester(label, abbreviation, start.localDate, end.localDate, examStart.localDate, id)
}

object SemesterDb {
  def from(protocol: SemesterProtocol, existingId: Option[UUID]) = {
    SemesterDb(protocol.label, protocol.abbreviation, protocol.start.sqlDate, protocol.end.sqlDate, protocol.examStart.sqlDate, DateTime.now.timestamp, None, existingId.getOrElse(UUID.randomUUID))
  }
}

object PostgresSemester {
  implicit val writes: Writes[PostgresSemester] = Json.writes[PostgresSemester]

  def isCurrent(semester: PostgresSemester): Boolean = {
    new Interval(semester.start.toDateTimeAtCurrentTime, semester.end.toDateTimeAtCurrentTime).containsNow
  }

}

object SemesterProtocol {
  implicit val reads: Reads[SemesterProtocol] = Json.reads[SemesterProtocol]
}