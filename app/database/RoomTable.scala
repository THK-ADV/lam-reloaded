package database

import java.sql.Timestamp
import java.util.UUID

import models.{Room, RoomProtocol, UniqueDbEntity}
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api._
import utils.date.DateTimeOps.DateTimeConverter

class RoomTable(tag: Tag) extends Table[RoomDb](tag, "ROOMS") with UniqueTable with LabelTable with DescriptionTable {
  def capacity = column[Int]("CAPACITY")

  override def * = (label, description, capacity, lastModified, invalidated, id) <> ((RoomDb.apply _).tupled, RoomDb.unapply)
}

case class RoomDb(label: String, description: String, capacity: Int, lastModified: Timestamp = DateTime.now.timestamp, invalidated: Option[Timestamp] = None, id: UUID = UUID.randomUUID) extends UniqueDbEntity {
  override def toUniqueEntity = Room(label, description, capacity, id)
}