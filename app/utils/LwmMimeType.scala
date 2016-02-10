package utils

import play.api.http.ContentTypes
import play.api.mvc.Accepting

import scala.language.implicitConversions

case class LwmMimeType(value: String)

object LwmMimeType {

  val loginV1Json = LwmMimeType("application/vnd.fhk.login.V1+json")
  val studentV1Json = LwmMimeType("application/vnd.fhk.student.V1+json")
  val employeeV1Json = LwmMimeType("application/vnd.fhk.employee.V1+json")
  val courseV1Json = LwmMimeType("application/vnd.fhk.course.V1+json")
  val degreeV1Json = LwmMimeType("application/vnd.fhk.degree.V1+json")
  val groupV1Json = LwmMimeType("application/vnd.fhk.group.V1+json")
  val labworkV1Json = LwmMimeType("application/vnd.fhk.labwork.V1+json")
  val roomV1Json = LwmMimeType("application/vnd.fhk.room.V1+json")
  val semesterV1Json = LwmMimeType("application/vnd.fhk.semester.V1+json")
  val refRoleV1Json = LwmMimeType("application/vnd.fhk.refRole.V1+json")
  val authorityV1Json = LwmMimeType("application/vnd.fhk.authority.V1+json")
  val roleV1Json = LwmMimeType("application/vnd.fhk.role.V1+json")
  val permissionV1Json = LwmMimeType("application/vnd.fhk.permission.V1+json")
  val entryTypeV1Json = LwmMimeType("application/vnd.fhk.entryType.V1+json")
  val labworkApplicationV1Json = LwmMimeType("application/vnd.fhk.labworkApplication.V1+json")
  val scheduleV1Json = LwmMimeType("application/vnd.fhk.schedule.V1+json")
  val timetableV1Json = LwmMimeType("application/vnd.fhk.timetable.V1+json")
  val blacklistV1Json = LwmMimeType("application/vnd.fhk.blacklist.V1+json")

  implicit def unboxMimeType(mime: LwmMimeType): String = mime.value
}

object LwmContentTypes extends ContentTypes {

  import play.api.mvc.Codec

  def loginV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.loginV1Json)

  def studentV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.studentV1Json)

  def employeeV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.employeeV1Json)

  def courseV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.courseV1Json)

  def degreeV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.degreeV1Json)

  def groupV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.groupV1Json)

  def labworkV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.labworkV1Json)

  def roomV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.roomV1Json)

  def semesterV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.semesterV1Json)

  def refRoleV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.refRoleV1Json)

  def authorityV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.authorityV1Json)

  def roleV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.roleV1Json)

  def permissionV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.permissionV1Json)

  def entryTypeV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.entryTypeV1Json)

  def labworkApplicationV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.labworkApplicationV1Json)

  def scheduleV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.scheduleV1Json)

  def timetableV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.timetableV1Json)

  def blacklistV1ContentType(implicit codec: Codec) = withCharset(LwmMimeType.blacklistV1Json)
}

object LwmAccepts {
  val LoginV1Accept = Accepting(LwmMimeType.loginV1Json)
  val StudentV1Accept = Accepting(LwmMimeType.studentV1Json)
  val EmployeeV1Accept = Accepting(LwmMimeType.employeeV1Json)
  val CourseV1Accept = Accepting(LwmMimeType.courseV1Json)
  val DegreeV1Accept = Accepting(LwmMimeType.degreeV1Json)
  val GroupV1Accept = Accepting(LwmMimeType.groupV1Json)
  val LabworkV1Accept = Accepting(LwmMimeType.labworkV1Json)
  val RoomV1Accept = Accepting(LwmMimeType.roomV1Json)
  val SemesterV1Accept = Accepting(LwmMimeType.semesterV1Json)
  val RefRoleV1Accept = Accepting(LwmMimeType.refRoleV1Json)
  val AuthorityV1Accept = Accepting(LwmMimeType.authorityV1Json)
  val RoleV1Accept = Accepting(LwmMimeType.roleV1Json)
  val PermissionV1Accept = Accepting(LwmMimeType.permissionV1Json)
  val EntryTypeV1Accept = Accepting(LwmMimeType.entryTypeV1Json)
  val LabworkApplicationV1Accept = Accepting(LwmMimeType.labworkApplicationV1Json)
  val ScheduleV1Accept = Accepting(LwmMimeType.scheduleV1Json)
  val TimetableV1Accept = Accepting(LwmMimeType.timetableV1Json)
  val BlacklistV1Accept = Accepting(LwmMimeType.blacklistV1Json)
}