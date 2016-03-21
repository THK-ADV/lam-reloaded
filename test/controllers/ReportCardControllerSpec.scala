package controllers

import java.util.UUID

import base.TestBaseDefinition
import models.users.Student
import models._
import org.joda.time.{LocalDate, LocalTime}
import org.openrdf.model.Value
import org.openrdf.model.impl.ValueFactoryImpl
import org.scalatest.WordSpec
import org.w3.banana.PointedGraph
import org.w3.banana.sesame.SesameModule
import play.api.http._
import play.api.libs.json.Json
import play.api.test.{FakeHeaders, FakeRequest}
import services.{RoleService, SessionHandlingService}
import store.sparql.{Initial, SelectClause, QueryExecutor}
import store.{Namespace, SesameRepository}
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.mock.MockitoSugar.mock
import play.api.test.Helpers._
import utils.LwmMimeType

import scala.util.{Failure, Success}

class ReportCardControllerSpec extends WordSpec with TestBaseDefinition with SesameModule {

  val repository = mock[SesameRepository]
  val roleService = mock[RoleService]
  val namespace = Namespace("http://lwm.gm.th-koeln.de")
  val sessionService = mock[SessionHandlingService]
  val factory = ValueFactoryImpl.getInstance()
  val qe = mock[QueryExecutor[SelectClause]]
  val query = Initial[Nothing, Nothing](store.sparql.select(""))(qe)

  val reportCardController: ReportCardController = new ReportCardController(repository, sessionService, namespace, roleService) {

    override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
      case _ => NonSecureBlock
    }

    override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
      case _ => NonSecureBlock
    }
  }

  val student = Student("systemId to pass", "last name to pass", "first name to pass", "email to pass", "regId to pass", UUID.randomUUID)
  val labwork = Labwork("label to pass", "desc to pass", UUID.randomUUID, UUID.randomUUID, UUID.randomUUID)
  val rooms = Vector(
    Room("room 1", "desc 1"),
    Room("room 2", "desc 2")
  )

  val reportCard = {
    import scala.util.Random._

    val entries = (0 until 5).map( n =>
      ReportCardEntry(n, n.toString, LocalDate.now.plusWeeks(n), LocalTime.now.plusHours(n), LocalTime.now.plusHours(n + 1), rooms(nextInt(rooms.size)).id, ReportCardEntryType.all)
    ).toSet

    ReportCard(student.id, labwork.id, entries)
  }

  "A ReportCardControllerSpec " should {

    "successfully update a report card entry type" in {
      import ReportCardEntryType._

      val entry = reportCard.entries.head
      val entryType = entry.entryTypes.head
      val toUpdate = ReportCardEntryType(entryType.entryType, !entryType.bool, entryType.int, entryType.id)
      val course = UUID.randomUUID()

      when(repository.update(anyObject())(anyObject(), anyObject())).thenReturn(Success(PointedGraph[repository.Rdf](factory.createBNode(""))))

      val request = FakeRequest(
        PUT,
        s"/courses/$course/reportCards/${reportCard.id}/entries/${entry.id}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.reportCardV1Json)),
        Json.toJson(toUpdate)
      )

      val result = reportCardController.updateReportCardEntryType(course.toString, reportCard.id.toString, entry.id.toString)(request)

      status(result) shouldBe OK
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "reportCardId" -> reportCard.id,
        "reportCardEntryId" -> entry.id,
        "assignmentEntryType" -> Json.toJson(toUpdate)
      )
    }

    "not update a report card entry type with invalid json data" in {
      val entry = reportCard.entries.head
      val entryType = entry.entryTypes.head
      val course = UUID.randomUUID()

      when(repository.update(anyObject())(anyObject(), anyObject())).thenReturn(Success(PointedGraph[repository.Rdf](factory.createBNode(""))))

      val brokenJson = Json.obj(
        "entryType" -> entryType.entryType,
        "id" -> entryType.id
      )
      val request = FakeRequest(
        PUT,
        s"/courses/$course/reportCards/${reportCard.id}/entries/${entry.id}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.reportCardV1Json)),
        brokenJson
      )

      val result = reportCardController.updateReportCardEntryType(course.toString, reportCard.id.toString, entry.id.toString)(request)

      status(result) shouldBe BAD_REQUEST
      contentType(result) shouldBe Some("application/json")
    }

    "not update a report card entry type when there is an exception" in {
      val entry = reportCard.entries.head
      val entryType = entry.entryTypes.head
      val toUpdate = ReportCardEntryType(entryType.entryType, !entryType.bool, entryType.int, entryType.id)
      val course = UUID.randomUUID()
      val errorMessage = "Oops, something went wrong"

      when(repository.update(anyObject())(anyObject(), anyObject())).thenReturn(Failure(new Throwable(errorMessage)))

      val request = FakeRequest(
        PUT,
        s"/courses/$course/reportCards/${reportCard.id}/entries/${entry.id}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> LwmMimeType.reportCardV1Json)),
        Json.toJson(toUpdate)
      )

      val result = reportCardController.updateReportCardEntryType(course.toString, reportCard.id.toString, entry.id.toString)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "errors" -> errorMessage
      )
    }

    "return a student's report card" in {
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(
        Map("card" -> List(factory.createURI(ReportCard.generateUri(reportCard)(namespace))))
      ))
      when(repository.get[ReportCard](anyObject())(anyObject())).thenReturn(Success(Some(reportCard)))

      val request = FakeRequest(
        GET,
        s"/reportCards/student/${student.id}"
      )
      val result = reportCardController.get(student.id.toString)(request)

      status(result) shouldBe OK
      contentType(result) shouldBe Some[String](LwmMimeType.reportCardV1Json)
      contentAsJson(result) shouldBe Json.toJson(reportCard)
    }

    "not return a student's report card when its not found" in {
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(
        Map("card" -> List.empty[Value])
      ))
      when(repository.get[ReportCard](anyObject())(anyObject())).thenReturn(Success(Some(reportCard)))

      val request = FakeRequest(
        GET,
        s"/reportCards/student/${student.id}"
      )
      val result = reportCardController.get(student.id.toString)(request)

      status(result) shouldBe NOT_FOUND
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "No such element..."
      )
    }

    "not return a student's report card when there are no cards at all" in {
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(
        Map("card" -> List(factory.createURI(ReportCard.generateUri(reportCard)(namespace))))
      ))
      when(repository.get[ReportCard](anyObject())(anyObject())).thenReturn(Success(None))

      val request = FakeRequest(
        GET,
        s"/reportCards/student/${student.id}"
      )
      val result = reportCardController.get(student.id.toString)(request)

      status(result) shouldBe NOT_FOUND
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "No such element..."
      )
    }

    "not return a student's report card when there is an exception" in {
      val errorMessage = "Oops, something went wrong"
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(
        Map("card" -> List(factory.createURI(ReportCard.generateUri(reportCard)(namespace))))
      ))
      when(repository.get[ReportCard](anyObject())(anyObject())).thenReturn(Failure(new Throwable(errorMessage)))

      val request = FakeRequest(
        GET,
        s"/reportCards/student/${student.id}"
      )
      val result = reportCardController.get(student.id.toString)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "errors" -> errorMessage
      )
    }

    "successfully get a single report card atomized" in {
      import ReportCard.atomicWrites

      val reportCardAtom = {
        val atomEntries = reportCard.entries.map(e =>
          ReportCardEntryAtom(e.index, e.label, e.date, e.start, e.end, rooms.find(_.id == e.room).get, e.entryTypes, e.id)
        )
        ReportCardAtom(student, labwork, atomEntries, reportCard.id)
      }

      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(
        Map("card" -> List(factory.createURI(ReportCard.generateUri(reportCard)(namespace))))
      ))
      doReturn(Success(Some(reportCard))).
        doReturn(Success(Some(student))).
        doReturn(Success(Some(labwork))).
        when(repository).get(anyObject())(anyObject())
      when(repository.getMany[Room](anyObject())(anyObject())).thenReturn(Success(rooms.toSet))

      val request = FakeRequest(
        GET,
        s"/atomic/reportCards/student/${student.id}"
      )
      val result = reportCardController.getAtomic(student.id.toString)(request)

      status(result) shouldBe OK
      contentType(result) shouldBe Some[String](LwmMimeType.reportCardV1Json)
      contentAsJson(result) shouldBe Json.toJson(reportCardAtom)
    }

    "not get a single report card atomized when student is not found" in {
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(
        Map("card" -> List(factory.createURI(ReportCard.generateUri(reportCard)(namespace))))
      ))
      doReturn(Success(Some(reportCard))).
        doReturn(Success(None)).
        doReturn(Success(Some(labwork))).
        when(repository).get(anyObject())(anyObject())
      when(repository.getMany[Room](anyObject())(anyObject())).thenReturn(Success(rooms.toSet))

      val request = FakeRequest(
        GET,
        s"/atomic/reportCards/student/${student.id}"
      )
      val result = reportCardController.getAtomic(student.id.toString)(request)

      status(result) shouldBe NOT_FOUND
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "No such element..."
      )
    }

    "not get a single report card atomized when there is an exception" in {
      val errorMessage = s"Oops, cant get the desired report card for some reason"

      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(
        Map("card" -> List(factory.createURI(ReportCard.generateUri(reportCard)(namespace))))
      ))
      doReturn(Success(Some(reportCard))).
        doReturn(Success(Some(student))).
        doReturn(Failure(new Throwable(errorMessage))).
        when(repository).get(anyObject())(anyObject())
      when(repository.getMany[Room](anyObject())(anyObject())).thenReturn(Success(rooms.toSet))

      val request = FakeRequest(
        GET,
        s"/atomic/reportCards/student/${student.id}"
      )
      val result = reportCardController.getAtomic(student.id.toString)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "errors" -> errorMessage
      )
    }
  }
}
