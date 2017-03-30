package bind

import base.SesameDbSpec
import models._
import org.w3.banana.PointedGraph

import scala.util.{Failure, Success}

class StudentBindingSpec extends SesameDbSpec {

  import bindings.{StudentDescriptor, dateTimeBinder, uuidBinder, uuidRefBinder}
  import ops._

  implicit val studentBinder = StudentDescriptor.binder

  val student = SesameStudent("mi1234", "Doe", "John", "11234567", "mi1234@gm.fh-koeln.de", SesameDegree.randomUUID)
  val studentGraph = URI(User.generateUri(student)).a(lwm.User)
    .--(lwm.systemId).->-(student.systemId)
    .--(lwm.lastname).->-(student.lastname)
    .--(lwm.firstname).->-(student.firstname)
    .--(lwm.registrationId).->-(student.registrationId)
    .--(lwm.enrollment).->-(student.enrollment)(ops, uuidRefBinder(SesameDegree.splitter))
    .--(lwm.email).->-(student.email)
    .--(lwm.invalidated).->-(student.invalidated)
    .--(lwm.id).->-(student.id).graph

  "A StudentBinding" should {

    "return a RDF graph representation of a student" in {
      val graph = student.toPG.graph

      graph isIsomorphicWith studentGraph shouldBe true
    }

    "return a student based on a RDF graph representation" in {
      val expectedStudent = PointedGraph[Rdf](URI(User.generateUri(student)), studentGraph).as[SesameStudent]

      expectedStudent match {
        case Success(s) =>
          s shouldEqual student
        case Failure(e) =>
          fail(s"Unable to deserialise student graph: $e")
      }
    }

    "return a student atom based on an RDF graph representation" in {
      import bindings.{DegreeDescriptor, StudentAtomDescriptor, StudentDescriptor}

      val degree = SesameDegree("degree", "abbrev")
      val student = SesameStudent("systemid", "lastname", "firstname", "email", "regid", degree.id)

      val studentAtom = SesameStudentAtom(student.systemId, student.lastname, student.firstname, student.email, student.registrationId, degree, student.invalidated, student.id)

      repo add degree
      repo add student

      repo.get[SesameStudentAtom](User.generateUri(student)) match {
        case Success(Some(atom)) =>
          atom shouldEqual studentAtom
        case Success(None) =>
          fail("There should exist one student")
        case Failure(e) =>
          fail(s"StudentAtom could not be deserialised: $e")
      }
    }
  }
}