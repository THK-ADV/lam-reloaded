package actors

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import base.TestBaseDefinition
import models.Degree
import models.security.{RefRole, Role, Roles}
import models.users.Student
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.WordSpec
import org.scalatest.mock.MockitoSugar.mock
import services.{LDAPService, SessionServiceActor}
import services.SessionServiceActor.{AuthenticationFailure, AuthenticationSuccess}
import store.bind.Bindings
import store.{LwmResolvers, Namespace, SesameRepository}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class SessionServiceActorSpec extends WordSpec with TestBaseDefinition {

  implicit val timeout = Timeout(5 seconds)
  implicit val system = ActorSystem("TestSystem")

  val ns = Namespace("http://lwm.gm.fh-koeln.de/")
  val repository = SesameRepository(ns)
  val ldap = mock[LDAPService]
  val resolver = new LwmResolvers(repository)
  val bindings = Bindings[repository.Rdf](ns)

  val user = Student("mi1111", "Last", "First", "Email", "111111", Degree.randomUUID, Student.randomUUID)
  val actorRef = system.actorOf(SessionServiceActor.props(ldap, resolver))

  "A SessionServiceActor" should {

    "block unauthorized users" in {
      when(ldap.authenticate(anyString(), anyString())).thenReturn(Future.successful(false))


      val future = actorRef ? SessionServiceActor.SessionRequest("", "")
      val result = Await.result(future, timeout.duration)

      result match {
        case a: AuthenticationFailure => a.message shouldBe "Invalid credentials"
        case _ => fail("Should not return a success")
      }
    }

    "not create a user if an appropriate role has not been found" in {
      when(ldap.authenticate(anyString(), anyString())).thenReturn(Future.successful(true))
      when(ldap.attributes(anyString())).thenReturn(Future.successful(user))

      val future = actorRef ? SessionServiceActor.SessionRequest(user.systemId, "")
      val result = Await.result(future, timeout.duration)

      result match {
        case a: AuthenticationFailure =>
          a.message shouldBe "No appropriate RefRole found while resolving user"
        case _ => fail("Should not return a success")
      }
    }

    "create a session when a user is authorized and contains entries" in {
      when(ldap.authenticate(anyString(), anyString())).thenReturn(Future.successful(true))
      when(ldap.attributes(anyString())).thenReturn(Future.successful(user))
      import bindings.RefRoleBinding._
      import bindings.permissionBinder

      val refrole1 = RefRole(None, Roles.admin.id)
      val refrole2 = RefRole(None, Roles.student.id)

      repository.add(refrole1)
      repository.add(refrole2)

      val future = actorRef ? SessionServiceActor.SessionRequest(user.systemId, "")
      val result = Await.result(future, timeout.duration)

      result match {
        case a: AuthenticationSuccess =>
          a.session.userId shouldBe user.id
          a.session.username shouldBe user.systemId
        case _ => fail("Should not return a failure")
      }
    }
  }


  override protected def beforeEach(): Unit = {
    repository.connection { conn =>
      repository.rdfStore.removeGraph(conn, repository.ns)
    }
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }
}
