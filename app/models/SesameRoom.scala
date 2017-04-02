package models

import java.util.UUID

import controllers.JsonSerialisation
import org.joda.time.DateTime
import play.api.libs.json.{Json, Reads, Writes}

case class SesameRoom(label: String, description: String, invalidated: Option[DateTime] = None, id: UUID = SesameRoom.randomUUID) extends UniqueEntity

case class SesameRoomProtocol(label: String, description: String)

object SesameRoom extends UriGenerator[SesameRoom] with JsonSerialisation[SesameRoomProtocol, SesameRoom, SesameRoom] {

  lazy val default = SesameRoom("tbd", "tbd")

  override implicit def reads: Reads[SesameRoomProtocol] = Json.reads[SesameRoomProtocol]

  override implicit def writes: Writes[SesameRoom] = Json.writes[SesameRoom]

  override implicit def writesAtom: Writes[SesameRoom] = writes

  override def base: String = "rooms"
}

case class PostgresRoom(label: String, description: String, id: UUID = PostgresRoom.randomUUID) extends UniqueEntity

case class RoomDb(label: String, description: String, invalidated: Option[DateTime] = None, id: UUID = PostgresRoom.randomUUID) extends UniqueEntity{
  def toRoom = PostgresRoom(label, description, id)
}

case class PostgresRoomProtocol(label: String, description: String)

object PostgresRoom extends UriGenerator[PostgresRoom] with JsonSerialisation[PostgresRoomProtocol, PostgresRoom, PostgresRoom] {

  lazy val default = PostgresRoom("tbd", "tbd")

  override implicit def reads: Reads[PostgresRoomProtocol] = Json.reads[PostgresRoomProtocol]

  override implicit def writes: Writes[PostgresRoom] = Json.writes[PostgresRoom]

  override implicit def writesAtom: Writes[PostgresRoom] = writes

  override def base: String = "rooms"
}