package security

import java.util.UUID

import base.{SecurityBaseDefinition, TestBaseDefinition}
import controllers.SessionController
import models.Permissions.{authority, _}
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

class RoleControllerSecuritySpec extends WordSpec with TestBaseDefinition with SecurityBaseDefinition {

  "A RoleControllerSecuritySpec " should {

    when(sessionService.isValid(Matchers.anyObject())).thenReturn(Future.successful(true))

    "Allow non restricted context invocations when admin wants to update a role" in new FakeApplication() {
      import models.SesamePermission.writes

      when(roleService.authorities(FakeAdmin)).thenReturn(Success(Set(FakeAdminAuth)))
      when(roleService.checkAuthority((None, prime))(FakeAdminAuth)).thenReturn(Success(true))

      val json = Json.obj(
        "label" -> "admin",
        "permissions" -> authority.all
      )

      val request = FakeRequest(
        PUT,
        s"/roles/${UUID.randomUUID()}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.roleV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeAdmin.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
    }

    "Block non restricted context invocations when rv wants to update a role" in new FakeApplication() {
      import models.SesamePermission.writes

      when(roleService.authorities(FakeRv)).thenReturn(Success(Set(FakeRvAuth)))
      when(roleService.checkAuthority((None, prime))(FakeRvAuth)).thenReturn(Success(false))

      val json = Json.obj(
        "label" -> "admin",
        "permissions" -> authority.all
      )

      val request = FakeRequest(
        PUT,
        s"/roles/${UUID.randomUUID()}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.roleV1Json)),
        json
      ).withSession(
        SessionController.userId -> FakeRv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe UNAUTHORIZED
    }

    "Allow non restricted context invocations when rv wants to get a single authority" in new FakeApplication() {
      when(roleService.authorities(FakeRv)).thenReturn(Success(Set(FakeRvAuth)))
      when(roleService.checkAuthority((None, role.get))(FakeRvAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"/roles/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeRv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe NOT_FOUND
    }
  }
}
