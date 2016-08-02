package models.security

import java.util.UUID

import controllers.crud.JsonSerialisation
import models.users.{Employee, Student, User}
import models.{Course, CourseAtom, UniqueEntity, UriGenerator}
import org.joda.time.DateTime
import play.api.libs.json._

/**
  * Structure linking a user to his/her respective authority in the system.
  * `Authority` is created in order to separate concerns between user data and
  * his/her permissions in the underlying system.
  * It abstracts over the set of all partial permissions a user has in the system.
  *
  * @param user     The referenced user
  * @param course   Referenced course/module
  * @param role     Reference to `Role` Instance of that course/module
  * @param id       Unique id of the `Authority`
  */

case class Authority(user: UUID, role: UUID, course: Option[UUID] = None, invalidated: Option[DateTime] = None, id: UUID = Authority.randomUUID) extends UniqueEntity

case class AuthorityProtocol(user: UUID, role: UUID, course: Option[UUID] = None)

case class AuthorityAtom(user: User, role: Role, course: Option[CourseAtom], invalidated: Option[DateTime] = None, id: UUID) extends UniqueEntity

/**
  * Structure abstracting over a set of unary `Permission`s.
  * These sets are aggregated to specific `Role`s such that default, reusable `Role`s are possible.
  * `Role`s are independent. They can only be referenced by other graphs.
  *
  * @param label       Name or label of the `Role`
  * @param permissions The unary permissions of that `Role`
  */

case class Role(label: String, permissions: Set[Permission], invalidated: Option[DateTime] = None, id: UUID = Role.randomUUID) extends UniqueEntity

case class RoleProtocol(label: String, permissions: Set[Permission])

object Roles {
  lazy val Admin = "Administrator"
  lazy val Employee = "Mitarbeiter"
  lazy val Student = "Student"
  lazy val CourseEmployee = "Modulmitarbeiter"
  lazy val CourseAssistant = "Hilfskraft"
  lazy val CourseManager = "Modulverantwortlicher"
  lazy val RightsManager = "Rechteverantwortlicher"
}

/**
  * A unary permission.
  *
  * @param value Raw permission label
  */

case class Permission(value: String) {
  override def toString: String = value
}

object Permission extends JsonSerialisation[Permission, Permission, Permission] {

  override implicit def reads: Reads[Permission] = Json.reads[Permission]

  override implicit def writes: Writes[Permission] = Json.writes[Permission]

  override def writesAtom: Writes[Permission] = writes
}

object Role extends UriGenerator[Role] with JsonSerialisation[RoleProtocol, Role, Role] {

  override implicit def reads: Reads[RoleProtocol] = Json.reads[RoleProtocol]

  override implicit def writes: Writes[Role] = Json.writes[Role]

  override def writesAtom: Writes[Role] = writes

  override def base: String = "roles"
}

object Authority extends UriGenerator[Authority] with JsonSerialisation[AuthorityProtocol, Authority, AuthorityAtom] {
  lazy val empty = Authority(UUID.randomUUID, UUID.randomUUID)

  override def base: String = "authorities"

  override implicit def reads: Reads[AuthorityProtocol] = Json.reads[AuthorityProtocol]

  override implicit def writes: Writes[Authority] = Json.writes[Authority]

  override implicit def writesAtom: Writes[AuthorityAtom] = Writes[AuthorityAtom] { authority =>
    def toJson(userJs: JsValue): JsObject = {
      def basic(user: JsValue): JsObject = Json.obj(
        "user" -> user,
        "role" -> Json.toJson(authority.role)
      )

      def withCourse(jsObject: JsObject): JsObject = {
        authority.course.fold(jsObject)(course => jsObject + ("course" -> Json.toJson(course)(Course.writesAtom)))
      }

      def withInvalidated(jsObject: JsObject): JsObject = {
        authority.invalidated.fold(jsObject)(timestamp => jsObject + ("withInvalidated" -> Json.toJson(timestamp)))
      }

      def withId(jsObject: JsObject): JsObject = jsObject + ("id" -> Json.toJson(authority.id.toString))

      (basic _ andThen withCourse andThen withInvalidated andThen withId)(userJs)
    }

    authority.user match {
      case student: Student => toJson(Json.toJson(student))
      case employee: Employee => toJson(Json.toJson(employee))
    }
  }
}