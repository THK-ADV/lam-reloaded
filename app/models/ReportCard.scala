package models

import java.sql.{Date, Time, Timestamp}
import java.util.UUID
import models.LwmDateTime._
import controllers.JsonSerialisation
import org.joda.time.{DateTime, LocalDate, LocalTime}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.Ops.JsPathX

/**
  * ReportCard
  */

case class SesameReportCardEntry(student: UUID, labwork: UUID, label: String, date: LocalDate, start: LocalTime, end: LocalTime, room: UUID, entryTypes: Set[SesameReportCardEntryType], rescheduled: Option[SesameRescheduled] = None, invalidated: Option[DateTime] = None, id: UUID = SesameReportCardEntry.randomUUID) extends UniqueEntity {

  override def equals(that: scala.Any): Boolean = that match {
    case SesameReportCardEntry(s, l, la, d, st, e, r, et, rs, _, i) =>
      s == student &&
        l == labwork &&
        la == label &&
        d.isEqual(date) &&
        st.isEqual(start) &&
        e.isEqual(end) &&
        r == room &&
        et == entryTypes &&
        rs == rescheduled &&
        i == id
    case None => false
  }
}

case class SesameReportCardEntryType(entryType: String, bool: Boolean = false, int: Int = 0, invalidated: Option[DateTime] = None, id: UUID = SesameReportCardEntryType.randomUUID) extends UniqueEntity

case class SesameReportCardEvaluation(student: UUID, labwork: UUID, label: String, bool: Boolean, int: Int, timestamp: DateTime = DateTime.now, invalidated: Option[DateTime] = None, id: UUID = SesameReportCardEvaluation.randomUUID) extends UniqueEntity

case class SesameRescheduled(date: LocalDate, start: LocalTime, end: LocalTime, room: UUID)

// POSTGRES

sealed trait ReportCardEntry extends UniqueEntity

// maybe we can get rid of rescheduled and retry dependency from reportCardEntry
case class PostgresReportCardEntry(student: UUID, labwork: UUID, label: String, date: LocalDate, start: LocalTime, end: LocalTime, room: UUID, entryTypes: Set[PostgresReportCardEntryType], rescheduled: Option[PostgresReportCardRescheduled] = None, retry: Option[PostgresReportCardRetry] = None, id: UUID = UUID.randomUUID) extends ReportCardEntry

case class PostgresReportCardEntryProtocol(student: UUID, labwork: UUID, label: String, date: LocalDate, start: LocalTime, end: LocalTime, room: UUID)

case class PostgresReportCardEntryType(entryType: String, bool: Option[Boolean] = None, int: Int = 0, id: UUID = UUID.randomUUID) extends UniqueEntity

case class PostgresReportCardEvaluation(student: UUID, labwork: UUID, label: String, bool: Boolean, int: Int, id: UUID = UUID.randomUUID) extends UniqueEntity

case class PostgresReportCardRescheduled(date: LocalDate, start: LocalTime, end: LocalTime, room: UUID, reason: Option[String] = None, id: UUID = UUID.randomUUID) extends UniqueEntity

case class PostgresReportCardRetry(date: LocalDate, start: LocalTime, end: LocalTime, room: UUID, entryTypes: Set[PostgresReportCardEntryType], reason: Option[String] = None, id: UUID = UUID.randomUUID) extends UniqueEntity

// DB

case class ReportCardEntryDb(student: UUID, labwork: UUID, label: String, date: Date, start: Time, end: Time, room: UUID, entryTypes: Set[ReportCardEntryTypeDb], rescheduled: Option[ReportCardRescheduledDb] = None, retry: Option[ReportCardRetryDb] = None, var lastModified: Timestamp = DateTime.now.timestamp, var invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {

  override def toLwmModel = PostgresReportCardEntry(
    student,
    labwork,
    label,
    date.localDate,
    start.localTime,
    end.localTime,
    room,
    entryTypes.map(_.toLwmModel),
    rescheduled.map(_.toLwmModel),
    retry.map(_.toLwmModel),
    id
  )

  /*def toReportCardEntry: PostgresReportCardEntry = toReportCardEntry(entryTypes.toSeq, rescheduled, retry.map(r => (r, r.entryTypes.toSeq)))

  def toReportCardEntry(types: Seq[ReportCardEntryTypeDb], optRs: Option[ReportCardRescheduledDb], optRt: Option[(ReportCardRetryDb, Seq[ReportCardEntryTypeDb])]): PostgresReportCardEntry = PostgresReportCardEntry(
    student,
    labwork,
    label,
    date.localDate,
    start.localTime,
    end.localTime,
    room,
    types.map(_.toReportCardEntryType).toSet,
    optRs.map(_.toReportCardRescheduled),
    optRt.map(r => r._1.toReportCardRetry(r._2)),
    id
  )*/

  override def equals(that: scala.Any) = that match {
    case ReportCardEntryDb(s, l, lb, dt, st, et, r, ts, rs, rt, _, _, i) =>
      s == student &&
      l == labwork &&
      lb == label &&
      dt.localDate.isEqual(date.localDate) &&
      st.localTime.isEqual(start.localTime) &&
      et.localTime.isEqual(end.localTime) &&
      r == room &&
      ts == entryTypes &&
      rs == rescheduled &&
      rt == retry &&
      i == id
    case _ => false
  }
}

case class ReportCardEntryTypeDb(reportCardEntry: Option[UUID], reportCardRetry: Option[UUID], entryType: String, bool: Option[Boolean] = None, int: Int = 0, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {
  override def toLwmModel = PostgresReportCardEntryType(entryType, bool, int, id)
}

case class ReportCardEvaluationDb(student: UUID, labwork: UUID, label: String, bool: Boolean, int: Int, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {
  override def toLwmModel = PostgresReportCardEvaluation(student, labwork, label, bool, int, id)
}

case class ReportCardRescheduledDb(reportCardEntry: UUID, date: Date, start: Time, end: Time, room: UUID, reason: Option[String] = None, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {
  override def toLwmModel = PostgresReportCardRescheduled(date.localDate, start.localTime, end.localTime, room, reason, id)

  override def equals(that: scala.Any) = that match {
    case ReportCardRescheduledDb(rc, dt, st, et, r, rs, _, _, i) =>
      rc == reportCardEntry &&
        dt.localDate.isEqual(date.localDate) &&
        st.localTime.isEqual(start.localTime) &&
        et.localTime.isEqual(end.localTime) &&
        r == room &&
        rs == reason &&
        i == id
  }
}

case class ReportCardRetryDb(reportCardEntry: UUID, date: Date, start: Time, end: Time, room: UUID, entryTypes: Set[ReportCardEntryTypeDb], reason: Option[String] = None, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {
  //def toReportCardRetry(types: Seq[ReportCardEntryTypeDb]) = PostgresReportCardRetry(date.localDate, start.localTime, end.localTime, room, types.map(_.toLwmModel).toSet, reason, id)

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

/**
  * Atomic
  */

case class SesameReportCardEntryAtom(student: SesameStudent, labwork: SesameLabwork, label: String, date: LocalDate, start: LocalTime, end: LocalTime, room: SesameRoom, entryTypes: Set[SesameReportCardEntryType], rescheduled: Option[SesameRescheduledAtom], invalidated: Option[DateTime] = None, id: UUID) extends UniqueEntity

case class SesameRescheduledAtom(date: LocalDate, start: LocalTime, end: LocalTime, room: SesameRoom)

case class SesameReportCardEvaluationAtom(student: SesameStudent, labwork: SesameLabworkAtom, label: String, bool: Boolean, int: Int, timestamp: DateTime, invalidated: Option[DateTime] = None, id: UUID) extends UniqueEntity

// POSTGRES

case class PostgresReportCardEntryAtom(student: User, labwork: PostgresLabwork, label: String, date: LocalDate, start: LocalTime, end: LocalTime, room: PostgresRoom, entryTypes: Set[PostgresReportCardEntryType], rescheduled: Option[PostgresReportCardRescheduledAtom], retry: Option[PostgresReportCardRetryAtom], id: UUID) extends ReportCardEntry

case class PostgresReportCardRescheduledAtom(date: LocalDate, start: LocalTime, end: LocalTime, room: PostgresRoom, reason: Option[String], id: UUID) extends UniqueEntity

case class PostgresReportCardRetryAtom(date: LocalDate, start: LocalTime, end: LocalTime, room: PostgresRoom, entryTypes: Set[PostgresReportCardEntryType], reason: Option[String], id: UUID) extends UniqueEntity

/**
  * Companions
  */

object SesameReportCardEntry extends UriGenerator[SesameReportCardEntry] with JsonSerialisation[SesameReportCardEntry, SesameReportCardEntry, SesameReportCardEntryAtom] {

  override def base: String = "reportCardEntries"

  override implicit def reads: Reads[SesameReportCardEntry] = Json.reads[SesameReportCardEntry]

  override implicit def writes: Writes[SesameReportCardEntry] = Json.writes[SesameReportCardEntry]

  override implicit def writesAtom: Writes[SesameReportCardEntryAtom] = SesameReportCardEntryAtom.writesAtom
}

object SesameReportCardEntryType extends UriGenerator[SesameReportCardEntryType] with JsonSerialisation[SesameReportCardEntryType, SesameReportCardEntryType, SesameReportCardEntryType] {

  def all = Set(Attendance, Certificate, Bonus, Supplement)

  def Attendance = SesameReportCardEntryType(SesameAssignmentEntryType.Attendance.entryType)

  def Certificate = SesameReportCardEntryType(SesameAssignmentEntryType.Certificate.entryType)

  def Bonus = SesameReportCardEntryType(SesameAssignmentEntryType.Bonus.entryType)

  def Supplement = SesameReportCardEntryType(SesameAssignmentEntryType.Supplement.entryType)

  override def base: String = "reportCardEntryTypes"

  override implicit def reads: Reads[SesameReportCardEntryType] = Json.reads[SesameReportCardEntryType]

  override def writesAtom: Writes[SesameReportCardEntryType] = writes

  override implicit def writes: Writes[SesameReportCardEntryType] = Json.writes[SesameReportCardEntryType]
}

object SesameReportCardEvaluation extends UriGenerator[SesameReportCardEvaluation] with JsonSerialisation[SesameReportCardEvaluation, SesameReportCardEvaluation, SesameReportCardEvaluationAtom] {

  override def base: String = "reportCardEvaluation"

  override implicit def reads: Reads[SesameReportCardEvaluation] = Json.reads[SesameReportCardEvaluation]

  override implicit def writes: Writes[SesameReportCardEvaluation] = (
    (JsPath \ "student").write[UUID] and
      (JsPath \ "labwork").write[UUID] and
      (JsPath \ "label").write[String] and
      (JsPath \ "bool").write[Boolean] and
      (JsPath \ "int").write[Int] and
      (JsPath \ "timestamp").write[DateTime](LwmDateTime.writes) and
      (JsPath \ "invalidated").writeNullable[DateTime] and
      (JsPath \ "id").write[UUID]
    ) (unlift(SesameReportCardEvaluation.unapply))

  override implicit def writesAtom: Writes[SesameReportCardEvaluationAtom] = SesameReportCardEvaluationAtom.writesAtom
}

object SesameRescheduled extends JsonSerialisation[SesameRescheduled, SesameRescheduled, SesameRescheduledAtom] {

  override implicit def reads: Reads[SesameRescheduled] = Json.reads[SesameRescheduled]

  override implicit def writes: Writes[SesameRescheduled] = Json.writes[SesameRescheduled]

  override implicit def writesAtom: Writes[SesameRescheduledAtom] = SesameRescheduledAtom.writesAtom
}

object SesameRescheduledAtom {

  implicit def writesAtom: Writes[SesameRescheduledAtom] = (
    (JsPath \ "date").write[LocalDate] and
      (JsPath \ "start").write[LocalTime] and
      (JsPath \ "end").write[LocalTime] and
      (JsPath \ "room").write[SesameRoom](SesameRoom.writes)
    ) (unlift(SesameRescheduledAtom.unapply))
}

object SesameReportCardEntryAtom {

  implicit def writesAtom: Writes[SesameReportCardEntryAtom] = (
    (JsPath \ "student").write[SesameStudent](SesameStudent.writes) and
      (JsPath \ "labwork").write[SesameLabwork] and
      (JsPath \ "label").write[String] and
      (JsPath \ "date").write[LocalDate] and
      (JsPath \ "start").write[LocalTime] and
      (JsPath \ "end").write[LocalTime] and
      (JsPath \ "room").write[SesameRoom](SesameRoom.writes) and
      (JsPath \ "entryTypes").writeSet[SesameReportCardEntryType] and
      (JsPath \ "rescheduled").writeNullable[SesameRescheduledAtom] and
      (JsPath \ "invalidated").writeNullable[DateTime] and
      (JsPath \ "id").write[UUID]
    ) (unlift(SesameReportCardEntryAtom.unapply))
}

object SesameReportCardEvaluationAtom {

  implicit def writesAtom: Writes[SesameReportCardEvaluationAtom] = (
    (JsPath \ "student").write[SesameStudent](SesameStudent.writes) and
      (JsPath \ "labwork").write[SesameLabworkAtom] and
      (JsPath \ "label").write[String] and
      (JsPath \ "bool").write[Boolean] and
      (JsPath \ "int").write[Int] and
      (JsPath \ "timestamp").write[DateTime](LwmDateTime.writes) and
      (JsPath \ "invalidated").writeNullable[DateTime] and
      (JsPath \ "id").write[UUID]
    ) (unlift(SesameReportCardEvaluationAtom.unapply))
}

// Postgres

object PostgresReportCardEntryType extends JsonSerialisation[PostgresReportCardEntryType, PostgresReportCardEntryType, PostgresReportCardEntryType] {

  def all = Set(Attendance, Certificate, Bonus, Supplement)

  def Attendance = PostgresReportCardEntryType(PostgresAssignmentEntryType.Attendance.entryType)

  def Certificate = PostgresReportCardEntryType(PostgresAssignmentEntryType.Certificate.entryType)

  def Bonus = PostgresReportCardEntryType(PostgresAssignmentEntryType.Bonus.entryType)

  def Supplement = PostgresReportCardEntryType(PostgresAssignmentEntryType.Supplement.entryType)

  override implicit def reads: Reads[PostgresReportCardEntryType] = Json.reads[PostgresReportCardEntryType]

  override def writesAtom: Writes[PostgresReportCardEntryType] = writes

  override implicit def writes: Writes[PostgresReportCardEntryType] = Json.writes[PostgresReportCardEntryType]
}