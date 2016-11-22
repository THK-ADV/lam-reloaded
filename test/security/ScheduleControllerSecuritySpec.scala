package security

import java.util.UUID

import base.{SecurityBaseDefinition, TestBaseDefinition}
import controllers.SessionController
import models.Permissions._
import models._
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

class ScheduleControllerSecuritySpec extends WordSpec with TestBaseDefinition with SecurityBaseDefinition {
  
  "A ScheduleControllerSecuritySpec " should {

    when(sessionService.isValid(Matchers.anyObject())).thenReturn(Future.successful(true))

    "Allow restricted context invocations when admin wants to update a schedule entry" in new FakeApplication() {
      import models.ScheduleEntry.writes

      when(roleService.authorities(FakeAdmin)).thenReturn(Success(Set(FakeAdminAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), scheduleEntry.update))(FakeAdminAuth)).thenReturn(Success(true))

      val entry = ScheduleEntry(UUID.randomUUID, LocalTime.now, LocalTime.now, LocalDate.now, UUID.randomUUID, Set(UUID.randomUUID), UUID.randomUUID)
      val request = FakeRequest(
        PUT,
        s"$FakeCourseUri/scheduleEntries/${entry.id}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.scheduleEntryV1Json)),
        Json.toJson(entry)
      ).withSession(
        SessionController.userId -> FakeAdmin.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Allow restricted context invocations when mv wants to create a schedule" in new FakeApplication() {
      import models.Schedule.writes

      when(roleService.authorities(FakeMv)).thenReturn(Success(Set(FakeMvAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), schedule.create))(FakeMvAuth)).thenReturn(Success(true))

      val json = Json.toJson(
        Schedule(UUID.randomUUID(), Set.empty[ScheduleEntry])
      )

      val request = FakeRequest(
        POST,
        s"$FakeCourseUri/schedules",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.scheduleV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeMv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe CREATED
    }

    "Allow restricted context invocations when mv wants to delete a schedule" in new FakeApplication() {
      when(roleService.authorities(FakeMv)).thenReturn(Success(Set(FakeMvAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), schedule.delete))(FakeMvAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        DELETE,
        s"$FakeCourseUri/schedules/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeMv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Allow restricted context invocations when mv wants to get a single schedule entry" in new FakeApplication() {
      when(roleService.authorities(FakeMv)).thenReturn(Success(Set(FakeMvAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), scheduleEntry.get))(FakeMvAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"$FakeCourseUri/scheduleEntries/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeMv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe NOT_FOUND
    }

    "Allow restricted context invocations when ma wants to get a single schedule entry" in new FakeApplication() {
      when(roleService.authorities(FakeMa)).thenReturn(Success(Set(FakeMaAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), scheduleEntry.get))(FakeMaAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"$FakeCourseUri/scheduleEntries/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeMa.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe NOT_FOUND
    }

    "Allow restricted context invocations when ma wants to get all schedule entries" in new FakeApplication() {
      when(roleService.authorities(FakeMa)).thenReturn(Success(Set(FakeMaAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), scheduleEntry.getAll))(FakeMaAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"$FakeCourseUri/scheduleEntries"
      ).withSession(
        SessionController.userId -> FakeMa.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Block restricted context invocations when ma wants to create schedule" in new FakeApplication() {
      import models.Schedule.writes

      when(roleService.authorities(FakeMa)).thenReturn(Success(Set(FakeMaAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), schedule.create))(FakeMaAuth)).thenReturn(Success(false))

      val json = Json.toJson(
        Schedule(UUID.randomUUID(), Set.empty[ScheduleEntry])
      )

      val request = FakeRequest(
        POST,
        s"$FakeCourseUri/schedules",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.scheduleV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeMa.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe UNAUTHORIZED
    }

    "Block restricted context invocations when ma wants to delete schedule" in new FakeApplication() {
      when(roleService.authorities(FakeMa)).thenReturn(Success(Set(FakeMaAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), schedule.delete))(FakeMaAuth)).thenReturn(Success(false))

      val request = FakeRequest(
        DELETE,
        s"$FakeCourseUri/schedules/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeMa.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe UNAUTHORIZED
    }

    "Block restricted context invocations when students wants to get a single schedule entry" in new FakeApplication() {
      when(roleService.authorities(FakeStudent)).thenReturn(Success(Set(FakeStudentAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), scheduleEntry.get))(FakeStudentAuth)).thenReturn(Success(false))

      val request = FakeRequest(
        GET,
        s"$FakeCourseUri/scheduleEntries/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeStudent.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe UNAUTHORIZED
    }

    "Block restricted context invocations when employee wants to get a single schedule entry" in new FakeApplication() {
      when(roleService.authorities(FakeEmployee)).thenReturn(Success(Set(FakeEmployeeAuth)))
      when(roleService.checkAuthority((Some(FakeCourse), scheduleEntry.get))(FakeEmployeeAuth)).thenReturn(Success(false))

      val request = FakeRequest(
        GET,
        s"$FakeCourseUri/scheduleEntries/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeEmployee.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe UNAUTHORIZED
    }
  }
}
