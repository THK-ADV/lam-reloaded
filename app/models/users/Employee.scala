package models.users

import java.util.UUID

import controllers.crud.JsonSerialisation
import models._
import play.api.libs.json.{Format, Json, Reads, Writes}

case class Employee(systemId: String, lastname: String, firstname: String, email: String, status: String, id: UUID = User.randomUUID) extends User

object Employee extends JsonSerialisation[Employee, Employee, Employee] {

  lazy val default = Employee("n.a.", "n.a.", "n.a", "n.a.", "n.a", User.randomUUID)

  override implicit def reads: Reads[Employee] = Json.reads[Employee]

  override implicit def writes: Writes[Employee] = Json.writes[Employee]

  override implicit def writesAtom: Writes[Employee] = writes

  implicit def format: Format[Employee] = Json.format[Employee]
}
