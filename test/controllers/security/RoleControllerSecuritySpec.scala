package controllers.security

import java.util.UUID

import base.TestBaseDefinition
import controllers.SessionController
import models.security.Permissions.authority
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.mock.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.{Application, ApplicationLoader}
import play.api.ApplicationLoader.Context
import play.api.test.{FakeHeaders, FakeRequest, WithApplicationLoader}
import services.RoleService
import utils.{LwmMimeType, DefaultLwmApplication}
import models.security.Permissions._

import scala.util.Success

class RoleControllerSecuritySpec extends WordSpec with TestBaseDefinition with SecurityBaseDefinition {

  "A RoleControllerSecuritySpec " should {

    "Allow non restricted context invocations when admin wants to update a role" in new FakeApplication() {
      import models.security.Permission.writes

      when(roleService.authorityFor(FakeAdmin.toString)).thenReturn(Success(Some(FakeAdminAuth)))
      when(roleService.checkWith((None, prime))(FakeAdminAuth)).thenReturn(Success(true))

      val json = Json.obj(
        "name" -> "admin",
        "permissions" -> authority.all
      )

      val request = FakeRequest(
        PUT,
        s"/roles/${UUID.randomUUID()}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.roleV1Json)),
        json
      ).withSession(SessionController.userId -> FakeAdmin.toString)

      val result = route(request).get

      status(result) shouldBe CREATED
    }

    "Block non restricted context invocations when rv wants to update a role" in new FakeApplication() {
      import models.security.Permission.writes

      when(roleService.authorityFor(FakeRv.toString)).thenReturn(Success(Some(FakeRvAuth)))
      when(roleService.checkWith((None, prime))(FakeRvAuth)).thenReturn(Success(false))

      val json = Json.obj(
        "name" -> "admin",
        "permissions" -> authority.all
      )

      val request = FakeRequest(
        PUT,
        s"/roles/${UUID.randomUUID()}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.roleV1Json)),
        json
      ).withSession(SessionController.userId -> FakeRv.toString)

      val result = route(request).get

      status(result) shouldBe UNAUTHORIZED
    }

    "Allow non restricted context invocations when rv wants to get a single authority" in new FakeApplication() {
      when(roleService.authorityFor(FakeRv.toString)).thenReturn(Success(Some(FakeRvAuth)))
      when(roleService.checkWith((None, role.get))(FakeRvAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"/roles/${UUID.randomUUID()}"
      ).withSession(SessionController.userId -> FakeRv.toString)

      val result = route(request).get

      status(result) shouldBe NOT_FOUND
    }
  }
}
