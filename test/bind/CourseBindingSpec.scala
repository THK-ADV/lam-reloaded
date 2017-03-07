package bind

import base.SesameDbSpec
import models.{SesameCourse$, SesameCourseAtom$, SesameEmployee, User}
import org.w3.banana.PointedGraph

import scala.util.{Failure, Success}

class CourseBindingSpec extends SesameDbSpec {

  import bindings.{
  CourseDescriptor,
  uuidBinder,
  dateTimeBinder,
  uuidRefBinder}
  import ops._

  implicit val courseBinder = CourseDescriptor.binder

  val course = SesameCourse("Algorithmen und Programmierung", "AP Victor", "AP", User.randomUUID, 1)
  val courseGraph = URI(SesameCourse.generateUri(course)).a(lwm.Course)
    .--(lwm.label).->-(course.label)
    .--(lwm.description).->-(course.description)
    .--(lwm.abbreviation).->-(course.abbreviation)
    .--(lwm.lecturer).->-(course.lecturer)(ops, uuidRefBinder(User.splitter))
    .--(lwm.semesterIndex).->-(course.semesterIndex)
    .--(lwm.invalidated).->-(course.invalidated)
    .--(lwm.id).->-(course.id).graph

  "A CourseBindingSpec" should {
    "return a RDF graph representation of a course" in {
      val graph = course.toPG.graph

      graph isIsomorphicWith courseGraph shouldBe true
    }

    "return a course based on a RDF graph representation" in {
      val expectedCourse = PointedGraph[Rdf](URI(SesameCourse.generateUri(course)), courseGraph).as[SesameCourse]

      expectedCourse match {
        case Success(s) =>
          s shouldEqual course
        case Failure(e) =>
          fail(s"Unable to deserialise course graph: $e")
      }
    }

    "return a course atom based on an RDF graph representation" in {
      import bindings.{
      EmployeeDescriptor,
      CourseDescriptor,
      CourseAtomDescriptor
      }

      val lecturer = SesameEmployee("systemid", "lastname", "firstname", "email", "lecturer")
      val course = SesameCourse("course", "description", "abbr", lecturer.id, 2)
      val courseAtom = SesameCourseAtom(course.label, course.description, course.abbreviation, lecturer, course.semesterIndex, course.invalidated, course.id)

      repo add lecturer
      repo add course

      repo.get[SesameCourseAtom](SesameCourse.generateUri(course)) match {
        case Success(Some(atom)) =>
          atom shouldEqual courseAtom
        case Success(None) =>
          fail("There should exist one course")
        case Failure(e) =>
          fail(s"CourseAtom could not be deserialised: $e")
      }
    }
  }
}
