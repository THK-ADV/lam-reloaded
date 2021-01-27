package models

import database.helper.LdapUserStatus

import java.util.UUID
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}

trait StudentLike extends User {
  def registrationId: String
  def enrollmentId: UUID
}

case class Student(systemId: String, lastname: String, firstname: String, email: String, registrationId: String, enrollment: UUID, id: UUID = UUID.randomUUID) extends StudentLike {
  override def enrollmentId = enrollment

  override def status = LdapUserStatus.StudentStatus
}

case class StudentAtom(systemId: String, lastname: String, firstname: String, email: String, registrationId: String, enrollment: Degree, id: UUID) extends StudentLike {
  override def enrollmentId = enrollment.id

  override def status = LdapUserStatus.StudentStatus
}

object Student {
  implicit val writes: Writes[Student] = Json.writes[Student]
}

object StudentAtom {

  implicit val writes: Writes[StudentAtom] = (
    (JsPath \ "systemId").write[String] and
      (JsPath \ "lastname").write[String] and
      (JsPath \ "firstname").write[String] and
      (JsPath \ "email").write[String] and
      (JsPath \ "registrationId").write[String] and
      (JsPath \ "enrollment").write[Degree](Degree.writes) and
      (JsPath \ "id").write[UUID]
    ) (unlift(StudentAtom.unapply))
}