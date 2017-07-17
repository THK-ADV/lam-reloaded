package security

import java.util.UUID

import base.{SecurityBaseDefinition, TestBaseDefinition}
import controllers.{ReportCardEntryController, SessionController}
import models._
import models.Permissions.reportCardEntry
import org.joda.time.{LocalDate, LocalTime}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.WordSpec
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import utils.LwmMimeType

import scala.concurrent.Future
import scala.util.Success

class ReportCardEntryControllerSecuritySpec extends WordSpec with TestBaseDefinition with SecurityBaseDefinition {

  when(sessionService.isValid(Matchers.anyObject())).thenReturn(Future.successful(true))

  val id = UUID.randomUUID
  val json = {
    import models.SesameReportCardEntry.writes

    Json.toJson(
      SesameReportCardEntry(UUID.randomUUID, UUID.randomUUID, "", LocalDate.now, LocalTime.now, LocalTime.now, UUID.randomUUID, Set.empty[SesameReportCardEntryType],
        Some(SesameRescheduled(LocalDate.now.plusDays(1), LocalTime.now.plusHours(1), LocalTime.now.plusHours(1), UUID.randomUUID)),
        None, id)
    )
  }

  "A ReportCardEntryControllerSecuritySpec " should {

    "Allow non restricted context invocations when admin wants to reschedule a report card entry" in new FakeApplication() {
      when(roleService.authorities(FakeAdmin)).thenReturn(Success(Set(FakeAdminAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), reportCardEntry.update))(FakeAdminAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        PUT,
        s"$FakeCourseUri/reportCardEntries/$id",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.reportCardEntryV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeAdmin.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Allow restricted context invocations when mv wants to reschedule a report card entry" in new FakeApplication() {
      when(roleService.authorities(FakeMv)).thenReturn(Success(Set(FakeMvAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), reportCardEntry.update))(FakeMvAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        PUT,
        s"$FakeCourseUri/reportCardEntries/$id",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.reportCardEntryV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeMv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Allow restricted context invocations when ma wants to reschedule a report card entry" in new FakeApplication() {
      when(roleService.authorities(FakeMa)).thenReturn(Success(Set(FakeMaAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), reportCardEntry.update))(FakeMaAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        PUT,
        s"$FakeCourseUri/reportCardEntries/$id",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.reportCardEntryV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeMa.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Block restricted context invocations when student wants to reschedule his own report card entry" in new FakeApplication() {
      when(roleService.authorities(FakeStudent)).thenReturn(Success(Set(FakeStudentAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), reportCardEntry.update))(FakeStudentAuth)).thenReturn(Success(false))

      val request = FakeRequest(
        PUT,
        s"$FakeCourseUri/reportCardEntries/$id",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.reportCardEntryV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeStudent.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe UNAUTHORIZED
    }

    "Allow non restricted context invocations when student wants to get his report card entries" in new FakeApplication() {
      when(roleService.authorities(FakeStudent)).thenReturn(Success(Set(FakeStudentAuth)))
      when(roleService.checkAuthority((None, reportCardEntry.get))(FakeStudentAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"/reportCardEntries/student/${UUID.randomUUID}"
      ).withSession(
        SessionController.userId -> FakeStudent.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Allow restricted context invocations when mv wants to get all report cards with filter" in new FakeApplication() {
      when(roleService.authorities(FakeMv)).thenReturn(Success(Set(FakeMvAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), reportCardEntry.getAll))(FakeMvAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"$FakeCourseUri/reportCardEntries?${ReportCardEntryController.studentAttribute}=${UUID.randomUUID}"
      ).withSession(
        SessionController.userId -> FakeMv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }
  }
}