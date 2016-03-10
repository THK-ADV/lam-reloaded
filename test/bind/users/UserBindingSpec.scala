package bind.users

import java.util.UUID

import base.TestBaseDefinition
import models.users.{Employee, Student, User}
import org.scalatest.WordSpec
import org.w3.banana.sesame.SesameModule
import store.Namespace
import store.bind.Bindings

import scala.util.Success

class UserBindingSpec extends WordSpec with TestBaseDefinition with SesameModule {

  import ops._

  val ns = Namespace("http://lwm.gm.bloody.norah.de")
  val bind = Bindings[Rdf](ns)

  "A UserBinding" should {

    "serialise and deserialise monomorphic collections of student entries" in {
      import bind.UserBinding._

      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", UUID.randomUUID(), Student.randomUUID)
      val student2 = Student("mi1818", "Sanh", "Tsruw", "alb@mail.de", "44332211", UUID.randomUUID(), Student.randomUUID)
      val student3 = Student("wi1818", "Nahs", "Rustw", "lab@mail.de", "22331144", UUID.randomUUID(), Student.randomUUID)

      val vec: Vector[User] = Vector(student1, student2, student3)

      val graphs = vec map (_.toPG)

      val res = graphs map (user => userBinder.fromPG(user))

      vec foreach { user =>
        res contains Success(user) shouldBe true
      }
    }

  "serialise and deserialise monomorphic collections of employee entries" in {
    import bind.UserBinding._


    val employee1 = Employee("mlark", "Lars", "Marklar", "mark@mail.de", "status", Employee.randomUUID)
    val employee2 = Employee("mlark", "Sarl", "Ralkram", "kram@mail.de", "status", Employee.randomUUID)
    val employee3 = Employee("rlak", "Rasl", "Kramral", "ramk@mail.de", "status", Employee.randomUUID)

    val vec: Vector[User] = Vector(employee1, employee2, employee3)

    val graphs = vec map (_.toPG)

    val res = graphs map (user => userBinder.fromPG(user))

    vec foreach { user =>
      res contains Success(user) shouldBe true
    }
  }

"serilaise and deserialise polymorphic collections of user entries" in {
      import bind.UserBinding._

      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", UUID.randomUUID(), Student.randomUUID)
      val student2 = Student("mi1818", "Sanh", "Tsruw", "alb@mail.de", "44332211", UUID.randomUUID(), Student.randomUUID)

      val employee1 = Employee("mlark", "Lars", "Marklar", "mark@mail.de", "status", Employee.randomUUID)
      val employee2 = Employee("mlark", "Sarl", "Ralkram", "kram@mail.de", "status", Employee.randomUUID)

      val vec: Vector[User] = Vector(student1, student2, employee1, employee2)

      val graphs = vec map (_.toPG)

      val res = graphs map (user => userBinder.fromPG(user))

      vec foreach { user =>
        res contains Success(user) shouldBe true
      }
    }
  }
}
