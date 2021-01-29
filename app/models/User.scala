package models

import database.helper.LdapUserStatus
import play.api.libs.json.{Json, Reads, Writes}

import java.util.UUID

trait User extends UniqueEntity {
  def systemId: String

  def lastname: String

  def firstname: String

  def email: String

  def status: LdapUserStatus
}

case class StudentProtocol(systemId: String, lastname: String, firstname: String, email: String, registrationId: String, enrollment: UUID)

object User {

  implicit val writes: Writes[User] = {
    case postgresStudent: Student => Json.toJson(postgresStudent)(Student.writes)
    case postgresStudentAtom: StudentAtom => Json.toJson(postgresStudentAtom)(StudentAtom.writes)
    case postgresEmployee: Employee => Json.toJson(postgresEmployee)(Employee.writes)
    case postgresLecturer: Lecturer => Json.toJson(postgresLecturer)(Lecturer.writes)
  }
}

object StudentProtocol {
  implicit val reads: Reads[StudentProtocol] = Json.reads[StudentProtocol]
}