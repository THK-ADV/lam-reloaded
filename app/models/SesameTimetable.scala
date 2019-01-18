package models

import java.sql.{Date, Time, Timestamp}
import java.util.UUID

import org.joda.time.{DateTime, LocalDate, LocalTime}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.LwmDateTime._
import utils.Ops.JsPathX

case class TimetableDateEntry(weekday: Weekday, date: LocalDate, start: LocalTime, end: LocalTime, room: UUID, supervisor: Set[UUID])

sealed trait Timetable extends UniqueEntity

case class PostgresTimetable(labwork: UUID, entries: Set[PostgresTimetableEntry], start: LocalDate, localBlacklist: Set[UUID], id: UUID = UUID.randomUUID) extends Timetable {
  override def equals(that: scala.Any) = that match {
    case PostgresTimetable(l, e, s, lb, i) =>
      l == labwork &&
        e == entries &&
        s.isEqual(start) &&
        lb == localBlacklist &&
        i == id
    case _ => false
  }
}

case class PostgresTimetableEntry(supervisor: Set[UUID], room: UUID, dayIndex: Int, start: LocalTime, end: LocalTime) {
  override def equals(that: scala.Any) = that match {
    case PostgresTimetableEntry(s, r, d, st, et) =>
      s == supervisor &&
        r == room &&
        d == dayIndex &&
        st.isEqual(start) &&
        et.isEqual(end)
    case _ => false
  }
}

case class PostgresTimetableProtocol(labwork: UUID, entries: Set[PostgresTimetableEntry], start: LocalDate, localBlacklist: Set[UUID])

case class PostgresTimetableAtom(labwork: PostgresLabwork, entries: Set[PostgresTimetableEntryAtom], start: LocalDate, localBlacklist: Set[PostgresBlacklist], id: UUID = UUID.randomUUID) extends Timetable

case class PostgresTimetableEntryAtom(supervisor: Set[User], room: PostgresRoom, dayIndex: Int, start: LocalTime, end: LocalTime)

case class TimetableDb(labwork: UUID, entries: Set[PostgresTimetableEntry], start: Date, localBlacklist: Set[UUID], lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {
  override def toLwmModel = PostgresTimetable(labwork, entries, start.localDate, localBlacklist, id)
}

case class TimetableEntryDb(timetable: UUID, room: UUID, supervisor: Set[UUID], dayIndex: Int, start: Time, end: Time, id: UUID = UUID.randomUUID) extends UniqueEntity {
  def toTimetableEntry = PostgresTimetableEntry(supervisor, room, dayIndex, start.localTime, end.localTime)
}

case class TimetableEntrySupervisor(timetableEntry: UUID, supervisor: UUID, id: UUID = UUID.randomUUID) extends UniqueEntity

case class TimetableBlacklist(timetable: UUID, blacklist: UUID, id: UUID = UUID.randomUUID) extends UniqueEntity

object TimetableDb {
  def from(p: PostgresTimetableProtocol, existing: Option[UUID]) = {
    TimetableDb(p.labwork, p.entries, p.start.sqlDate, p.localBlacklist, id = existing getOrElse UUID.randomUUID)
  }
}

object PostgresTimetable {
  implicit val writes: Writes[PostgresTimetable] = Json.writes[PostgresTimetable]
}

object PostgresTimetableProtocol {
  implicit val reads: Reads[PostgresTimetableProtocol] = Json.reads[PostgresTimetableProtocol]
}

object PostgresTimetableEntry {
  implicit val reads: Reads[PostgresTimetableEntry] = Json.reads[PostgresTimetableEntry]

  implicit val writes: Writes[PostgresTimetableEntry] = Json.writes[PostgresTimetableEntry]
}

object PostgresTimetableEntryAtom {

  implicit val writes: Writes[PostgresTimetableEntryAtom] = (
    (JsPath \ "supervisor").writeSet[User] and
      (JsPath \ "room").write[PostgresRoom](PostgresRoom.writes) and
      (JsPath \ "dayIndex").write[Int] and
      (JsPath \ "start").write[LocalTime] and
      (JsPath \ "end").write[LocalTime]
    ) (unlift(PostgresTimetableEntryAtom.unapply))
}

object PostgresTimetableAtom {

  implicit val writes: Writes[PostgresTimetableAtom] = (
    (JsPath \ "labwork").write[PostgresLabwork](PostgresLabwork.writes) and
      (JsPath \ "entries").writeSet[PostgresTimetableEntryAtom] and
      (JsPath \ "start").write[LocalDate] and
      (JsPath \ "localBlacklist").writeSet[PostgresBlacklist](PostgresBlacklist.writes) and
      (JsPath \ "id").write[UUID]
    ) (unlift(PostgresTimetableAtom.unapply))
}

object Timetable {

  implicit val writes: Writes[Timetable] = new Writes[Timetable] {
    override def writes(t: Timetable) = t match {
      case timetable: PostgresTimetable => Json.toJson(timetable)(PostgresTimetable.writes)
      case atom: PostgresTimetableAtom => Json.toJson(atom)(PostgresTimetableAtom.writes)
    }
  }
}
