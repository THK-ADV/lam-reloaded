package controllers.security

import java.util.UUID

import base.TestBaseDefinition
import controllers.SessionController
import models.security.Permissions._
import models.security.RefRole
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.WordSpec
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future
import scala.util.Success

class RefRoleControllerSecuritySpec extends WordSpec with TestBaseDefinition with SecurityBaseDefinition {

  "A RefRoleControllerSecuritySpec " should {

    when(sessionService.isValid(Matchers.anyObject())).thenReturn(Future.successful(true))

    "Allow non restricted context invocations when admin wants to get all refroles" in new FakeApplication() {
      import RefRole.writes

      when(roleService.authorityFor(FakeAdmin.toString)).thenReturn(Success(Some(FakeAdminAuth)))
      when(roleService.checkWith((None, refRole.getAll))(FakeAdminAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"/refRoles"
      ).withSession(
        SessionController.userId -> FakeAdmin.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(Set.empty[RefRole])
    }

    "Allow non restricted context invocations when rv wants to get a single refrole" in new FakeApplication() {

      when(roleService.authorityFor(FakeMv.toString)).thenReturn(Success(Some(FakeMvAuth)))
      when(roleService.checkWith((None, refRole.get))(FakeMvAuth)).thenReturn(Success(true))

      val request = FakeRequest(
        GET,
        s"/refRoles/${UUID.randomUUID()}"
      ).withSession(
        SessionController.userId -> FakeMv.toString,
        SessionController.sessionId -> UUID.randomUUID.toString
      )

      val result = route(request).get

      status(result) shouldBe NOT_FOUND
    }
  }
}
