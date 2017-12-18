package models

import java.sql.{Date, Time, Timestamp}
import java.util.UUID
import utils.LwmDateTime._
import controllers.JsonSerialisation
import org.joda.time.{DateTime, LocalDate, LocalTime}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.Ops.JsPathX

case class PostgresReportCardRetry(date: LocalDate, start: LocalTime, end: LocalTime, room: UUID, entryTypes: Set[PostgresReportCardEntryType], reason: Option[String] = None, id: UUID = UUID.randomUUID) extends UniqueEntity
case class PostgresReportCardRetryAtom(date: LocalDate, start: LocalTime, end: LocalTime, room: PostgresRoom, entryTypes: Set[PostgresReportCardEntryType], reason: Option[String], id: UUID) extends UniqueEntity

case class ReportCardRetryDb(reportCardEntry: UUID, date: Date, start: Time, end: Time, room: UUID, entryTypes: Set[ReportCardEntryTypeDb], reason: Option[String] = None, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {

  override def equals(that: scala.Any) = that match {
    case ReportCardRetryDb(rc, dt, st, et, r, ts, rs, _, _, i) =>
      rc == reportCardEntry &&
        dt.localDate.isEqual(date.localDate) &&
        st.localTime.isEqual(start.localTime) &&
        et.localTime.isEqual(end.localTime) &&
        r == room &&
        ts == entryTypes &&
        rs == reason &&
        i == id
  }

  override def toLwmModel = PostgresReportCardRetry(date.localDate, start.localTime, end.localTime, room, entryTypes.map(_.toLwmModel), reason, id)
}

object PostgresReportCardRetry extends JsonSerialisation[PostgresReportCardRetry, PostgresReportCardRetry, PostgresReportCardRetryAtom] {
  override implicit def reads = Json.reads[PostgresReportCardRetry]

  override implicit def writes = Json.writes[PostgresReportCardRetry]

  override implicit def writesAtom = PostgresReportCardRetryAtom.writesAtom
}

object PostgresReportCardRetryAtom {
  implicit def writesAtom: Writes[PostgresReportCardRetryAtom] = (
    (JsPath \ "date").write[LocalDate] and
      (JsPath \ "start").write[LocalTime] and
      (JsPath \ "end").write[LocalTime] and
      (JsPath \ "room").write[PostgresRoom](PostgresRoom.writes) and
      (JsPath \ "entryTypes").writeSet[PostgresReportCardEntryType](PostgresReportCardEntryType.writes) and
      (JsPath \ "reason").writeNullable[String] and
      (JsPath \ "id").write[UUID]
    ) (unlift(PostgresReportCardRetryAtom.unapply))
}