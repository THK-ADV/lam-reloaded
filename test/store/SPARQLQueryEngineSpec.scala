package store

import java.util.UUID

import base.TestBaseDefinition
import models.Degree
import models.users.Student
import org.scalatest.WordSpec
import org.w3.banana.sesame.{Sesame, SesameModule}
import store.Prefixes.LWMPrefix
import store.bind.Bindings
import utils.Ops._
import MonadInstances._


class SPARQLQueryEngineSpec extends WordSpec with TestBaseDefinition with SesameModule {

  implicit val ns = Namespace("http://lwm.gm.fh-koeln.de/")

  val bindings = Bindings[Sesame](ns)

  lazy val repo = SesameRepository(ns)

  lazy val prefixes = LWMPrefix[repo.Rdf]

  import bindings.StudentBinding._


  "A SPARQLQueryEngine" should {

    "execute select queries" in {
      import utils.Ops._

      val student = Student("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", Degree.randomUUID, Student.randomUUID)

      repo add student

      val result = repo.selectOperation.map { v =>
        sequence(v map { bs =>
          repo.get[Student](bs.getValue("s").stringValue()).toOption.flatten
        })
      } <>
        s"""
           |Select ?s where {
           |?s <${prefixes.systemId}> "${student.systemId}"
           |}
        """.stripMargin

      result.flatten match {
        case Some(s) => s.head shouldBe student
        case _ => fail("Query returned nothing")
      }

    }

    "execute ask queries" in {
      val anotherStudent = Student("mi1112", "Carlo", "Heinz", "117273", "mi1112@gm.fh-koeln.de", Degree.randomUUID, Student.randomUUID)

      repo add anotherStudent

      val result = repo.askOperation <>
        s"""
           |ASK {
           |?s <${prefixes.systemId}> "${anotherStudent.systemId}"
           |}
         """.stripMargin

      result match {
        case Some(v) => v shouldBe true
        case _ => fail("Result was not true")
      }
    }

    "play well with the SPARQL DSL" in {
      import store.sparql._
      import store.sparql.select._

      val student = Student("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", Degree.randomUUID, Student.randomUUID)

      val query =
        (select("s") where {
          ^(v("s"), p(prefixes.systemId), o(student.systemId))
        }).run

      repo add student

      val result = repo.selectOperation.map { v =>
        sequence(v map { bs =>
          repo.get[Student](bs.getValue("s").stringValue()).toOption.flatten
        })
      } <> query

      result.flatten match {
        case Some(s) => s.head shouldBe student
        case _ => fail("Query returned nothing")
      }
    }

    "allow inversions to left-associativity" in {
      import store.sparql._
      import store.sparql.select._

      val student = Student("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", Degree.randomUUID, Student.randomUUID)

      val clause =
        select("s", "id") where {
          ^(v("s"), p(prefixes.systemId), o(student.systemId)).
            ^(v("s"), p(prefixes.id), v("id"))
        }

      repo add student

      val result = repo.query(clause).flatMap(_.get("id")).flatMap(_.headOption).map(v => UUID.fromString(v.stringValue()))

      result match {
        case Some(s) => s shouldBe student.id
        case _ => fail("Query returned nothing")
      }
    }
  }

  override protected def beforeEach(): Unit = {
    repo.reset().foreach(r => assert(repo.size == 0))
  }

  override protected def beforeAll(): Unit = {
    repo.reset().foreach(r => assert(repo.size == 0))
  }

}
