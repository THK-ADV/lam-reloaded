package actors

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import base.TestBaseDefinition
import models.Degree
import models.security._
import models.users.Student
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.WordSpec
import org.scalatest.mock.MockitoSugar.mock
import services.{LdapService, SessionServiceActor}
import services.SessionServiceActor.{Authenticated, Authentication, AuthenticationError, NotAuthenticated}
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
  val ldap = mock[LdapService]
  val resolver = new LwmResolvers(repository)
  val bindings = Bindings[repository.Rdf](ns)

  val user = Student("mi1111", "Last", "First", "Email", "111111", Degree.randomUUID)
  val actorRef = system.actorOf(SessionServiceActor.props(ldap, resolver))

  "A SessionServiceActor" should {

    "block unauthorized users" in {
      when(ldap.authenticate(anyString(), anyString())).thenReturn(Future.successful(false))

      val future = (actorRef ? SessionServiceActor.SessionRequest("", "")).mapTo[Authentication]
      val result = Await.result(future, timeout.duration)

      result match {
        case NotAuthenticated(invalid) => invalid.message shouldBe "Invalid credentials"
        case _ => fail("Should not return a success")
      }
    }

    "not create a user if an appropriate role has not been found" in {
      when(ldap.authenticate(anyString(), anyString())).thenReturn(Future.successful(true))
      when(ldap.user(anyString())(anyObject())).thenReturn(Future.successful(user))

      val future = (actorRef ? SessionServiceActor.SessionRequest(user.systemId, "")).mapTo[Authentication]
      val result = Await.result(future, timeout.duration)

      result match {
        case AuthenticationError(error) =>
          error.getMessage shouldBe "No appropriate RefRole or Role found while resolving user"
        case _ => fail("Should not return a success")
      }
    }

    "create a session when a user is authorized and contains entries" in {
      import bindings.RoleDescriptor

      when(ldap.authenticate(anyString(), anyString())).thenReturn(Future.successful(true))
      when(ldap.user(anyString())(anyObject())).thenReturn(Future.successful(user))

      val studentRole = Role(Roles.Student, Set(Permissions.labworkApplication.create))
      val employeeRole = Role(Roles.Employee, Set(Permissions.course.create, Permissions.timetable.create))

      repository.add[Role](studentRole)
      repository.add[Role](employeeRole)

      val future = (actorRef ? SessionServiceActor.SessionRequest(user.systemId, "")).mapTo[Authentication]
      val result = Await.result(future, timeout.duration)

      result match {
        case Authenticated(session) =>
          session.userId shouldBe user.id
          session.username shouldBe user.systemId
        case _ => fail("Should not return a failure")
      }
    }
  }


  override protected def beforeEach(): Unit = {
    repository.connect { conn =>
      repository.rdfStore.removeGraph(conn, repository.ns)
    }
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }
}
