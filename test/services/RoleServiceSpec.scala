package services

import java.util.UUID

import base.TestBaseDefinition
import models.security._
import models.semester.Semester
import models.users.Student
import models.{AssignmentPlan, Degree, Labwork}
import org.scalatest.WordSpec
import org.w3.banana.sesame.SesameModule
import store.bind.Bindings
import store.{Namespace, SesameRepository}

import scala.util.Success

class RoleServiceSpec extends WordSpec with TestBaseDefinition with SesameModule {

  val sufficientPermissions = Set(Permission("view"), Permission("create"), Permission("delete"))
  val insufficientPermissions = Set(Permission("view"), Permission("delete"))

  val module1 = UUID.randomUUID()
  val module2 = UUID.randomUUID()

  val lab1 = Labwork("lab1", "", Semester.randomUUID, module1, Degree.randomUUID)
  val lab2 = Labwork("lab2", "", Semester.randomUUID, module2, Degree.randomUUID)

  val role1 = Role("testRole1", sufficientPermissions)
  val role2 = Role("testRole2", insufficientPermissions)
  val role3 = Role("Admin", Set(Permissions.prime))

  val roles = Vector(role1, role2, role3)

  val noneModule1Role1 = RefRole(None, role1.id)
  val noneModule1Role2 = RefRole(None, role2.id)

  val module1UserRole1 = RefRole(Some(module1), role1.id)
  val module1UserRole2 = RefRole(Some(module1), role2.id)
  val module2UserRole2 = RefRole(Some(module2), role2.id)
  val adminRefRole = RefRole(None, role3.id)

  def unbox(r: RefRole): (Option[UUID], Set[Permission]) = (r.module, roles.find(_.id == r.role).get.permissions)

  val ns = Namespace("http://lwm.gm.fh-koeln.de/")

  val repository = SesameRepository(ns)

  val bindings = Bindings(ns)

  def authority(refRoles: Set[RefRole]): Authority = Authority(UUID.randomUUID(), refRoles map (_.id))

  def roleService = new RoleService(repository)

  "A role service" should {

    "check refroles properly" in {
      import bindings.RoleBinding._
      import bindings.LabworkBinding._
      import bindings.RefRoleBinding._
      import util.Random.nextInt

      repository.addMany(roles)
      repository.add(lab1)
      repository.add(lab2)
      repository.add(module1UserRole1)
      repository.add(module1UserRole2)
      repository.add(module2UserRole2)
      repository.add(noneModule1Role1)
      repository.add(noneModule1Role2)
      repository.add(adminRefRole)

      val perm1 = role1.permissions.toVector
      val perm2 = role2.permissions.toVector

      val result1 = roleService.checkWith((Some(module1), perm1(nextInt(perm1.size))))(authority(Set(module1UserRole2)))
      val result2 = roleService.checkWith((Some(module1), perm1(nextInt(perm1.size))))(authority(Set(module1UserRole1, module2UserRole2)))
      val result3 = roleService.checkWith((None, perm1(nextInt(perm1.size))))(authority(Set(module1UserRole1, noneModule1Role1, module2UserRole2)))
      val result4 = roleService.checkWith((Some(module1), perm1(nextInt(perm1.size))))(authority(Set(adminRefRole)))
      val result5 = roleService.checkWith((Some(module2), perm2(nextInt(perm2.size))))(authority(Set(module1UserRole1)))
      val result6 = roleService.checkWith((Some(UUID.randomUUID()), perm1(nextInt(perm1.size))))(authority(Set(adminRefRole)))

      for {
        r1 <- result1
        r2 <- result2
        r3 <- result3
        r4 <- result4
        r5 <- result5
        r6 <- result6
      } yield {
        r1 shouldBe false
        r2 shouldBe true
        r3 shouldBe true
        r4 shouldBe true
        r5 shouldBe false
        r6 shouldBe true
      }
    }


    "retrieve authorities properly" in {
      import bindings.RoleBinding._
      import bindings.AuthorityBinding._
      import bindings.StudentBinding._
      import bindings.RefRoleBinding._

      val student1 = Student("mi1018", "last name", "first name", "email", "registrationId", Degree.randomUUID)
      val student2 = Student("ai1223", "last name", "first name", "email", "registrationId", Degree.randomUUID)
      val student3 = Student("ti1233", "last name", "first name", "email", "registrationId", Degree.randomUUID)

      val authority1 = Authority(student1.id, Set(module1UserRole1.id, module2UserRole2.id))
      val authority2 = Authority(student2.id, Set(module2UserRole2.id, noneModule1Role1.id))

      repository.add(student1)
      repository.add(student2)
      repository.add(student3)
      repository.add(module1UserRole1)
      repository.add(module2UserRole2)
      repository.add(role1)
      repository.add(role2)
      repository.add(role3)
      repository.add(authority1)
      repository.add(authority2)

      val result1 = roleService.authorityFor(student1.id.toString)
      val result2 = roleService.authorityFor(student2.id.toString)
      val result3 = roleService.authorityFor(student3.id.toString)

      (result1, result2, result3) match {
        case (Success(Some(r1)), Success(Some(r2)), Success(None)) =>
          r1 shouldBe authority1
          r2 shouldBe authority2
        case _ => fail("Should have found two of the authorities")
      }
    }
  }

}
