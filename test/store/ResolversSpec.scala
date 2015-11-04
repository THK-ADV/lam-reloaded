package store

import base.TestBaseDefinition
import models.Degree
import models.users.{Employee, Student}
import org.scalatest.WordSpec
import org.w3.banana.sesame.{Sesame, SesameModule}
import store.Prefixes.LWMPrefix
import store.bind.Bindings
import services.RoleService
import models.security.{Roles, Role, Authority}

import scala.util.{Failure, Success}
import scalaz.FingerTree

class ResolversSpec extends WordSpec with TestBaseDefinition with SesameModule {

  implicit val ns = Namespace("http://lwm.gm.fh-koeln.de/")

  val bindings = Bindings[Sesame](ns)
  val lwm = LWMPrefix[Sesame]

  import bindings.StudentBinding._
  import bindings.AuthorityBinding._
  import bindings.RoleBinding._
  import bindings.permissionBinder
  import bindings.EmployeeBinding._

  val repo = SesameRepository(ns)

  val resolver = new LwmResolvers(repo)
  val roleService = new RoleService(repo)

  "A UsernameResolverSpec " should {
    "resolve a given username properly" in {
      val student1 = Student("mi1018", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)
      val student2 = Student("ai1223", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)
      val student3 = Student("ti1233", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)

      val previousSize = repo.size

      repo.add(student1)
      repo.add(student2)
      repo.add(student3)

      val result = resolver.username(student1.systemId)

      result match {
        case Some(uuid) =>
          previousSize shouldEqual 0
          repo.size > previousSize shouldBe true
          uuid shouldEqual student1.id
        case None =>
          fail("uuid is none")
      }
    }

    "return None when username is not found" in {
      val student1 = Student("mi1111", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)
      val student2 = Student("ai1223", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)
      val student3 = Student("ti1233", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)

      repo.add(student1)
      repo.add(student2)
      repo.add(student3)

      val result = resolver.username("ai111")

      result shouldBe None
    }

    "resolve a student, employee and their authorities when non-existent" in {
      val student1 = Student("mi1111", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)
      val employee = Employee("system id", "last name", "first name", "email", Employee.randomUUID)

      repo.add[Role](Roles.student)
      repo.add[Role](Roles.user)

      resolver.missingUserData(student1)
      resolver.missingUserData(employee)

      val studentResult = repo.get[Student](Student.generateUri(student1)(repo.namespace)).toOption.flatten
      val studentAuth = roleService.authorityFor(student1.id.toString)

      val employeeResult = repo.get[Employee](Employee.generateUri(employee)(repo.namespace)).toOption.flatten
      val employeeAuth = roleService.authorityFor(employee.id.toString)

      (studentResult, studentAuth) match {
        case (Some(student), Some(auth)) =>
          student shouldBe student1
          auth.user shouldBe student1.id
          auth.refRoles.exists(_.role == Roles.student.id) shouldBe true
          auth.refRoles.size shouldBe 1
        case (None, _) => fail("Could not retrieve student")

        case (_, None) => fail("Authority either not created or not found")
      }

      (employeeResult, employeeAuth) match {
        case (Some(emp), Some(auth)) =>
          emp shouldBe employee
          auth.user shouldBe employee.id
          auth.refRoles.exists(_.role == Roles.user.id) shouldBe true
          auth.refRoles.size shouldBe 1
        case (None, _) => fail("Could not retrieve user")

        case (_, None) => fail("Authority either not created or not found")
      }
    }

    "stop trying to resolve somebody when other dependencies fail" in {
      val student1 = Student("mi1111", "last name", "first name", "email", "registrationId", Degree.randomUUID, Student.randomUUID)

      resolver.missingUserData(student1)

      val result = repo.get[Student](Student.generateUri(student1)(repo.namespace)).toOption.flatten
      val possibleAuthority = roleService.authorityFor(student1.id.toString)


      (result, possibleAuthority) match {
        case (Some(student), Some(auth)) => fail("Neither the student n'or his authority should be found")
        case (s, Some(_)) => fail("No authority should have been created")
        case (s, None) =>
          s shouldBe None
          repo.size shouldBe 0
      }
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    repo.reset()
  }
}
