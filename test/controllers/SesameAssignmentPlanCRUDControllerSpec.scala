package controllers

import java.util.UUID

import base.StreamHandler
import base.StreamHandler._
import models._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.w3.banana.PointedGraph
import org.w3.banana.sesame.Sesame
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import utils.LwmMimeType

import scala.util.{Failure, Success}

class SesameAssignmentPlanCRUDControllerSpec extends AbstractCRUDControllerSpec[SesameAssignmentPlanProtocol, SesameAssignmentPlan, SesameAssignmentPlanAtom] {

  override def entityTypeName: String = "assignmentPlan"

  override val controller: AssignmentPlanCRUDController = new AssignmentPlanCRUDController(repository, sessionService, namespace, roleService) {

    override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
      case _ => NonSecureBlock
    }

    override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
      case _ => NonSecureBlock
    }

    override protected def fromInput(input: SesameAssignmentPlanProtocol, existing: Option[SesameAssignmentPlan]): SesameAssignmentPlan = entityToPass
  }

  val labworkToPass = SesameLabwork("label to pass", "desc to pass", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

  val labworkToFail = SesameLabwork("label to fail", "desc to fail", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

  override val entityToFail: SesameAssignmentPlan = SesameAssignmentPlan(labworkToFail.id, 5, 5, entries("fail"))

  override val entityToPass: SesameAssignmentPlan = SesameAssignmentPlan(labworkToPass.id, 5, 5, entries("pass"))

  override val atomizedEntityToPass: SesameAssignmentPlanAtom =
    SesameAssignmentPlanAtom(
      labworkToPass,
      entityToPass.attendance,
      entityToPass.mandatory,
      Set.empty[SesameAssignmentEntry],
      entityToPass.invalidated,
      entityToPass.id)

  override val atomizedEntityToFail: SesameAssignmentPlanAtom =
    SesameAssignmentPlanAtom(
      labworkToFail,
      entityToFail.attendance,
      entityToFail.mandatory,
      Set.empty[SesameAssignmentEntry],
      entityToPass.invalidated,
      entityToFail.id)


  override implicit val jsonWrites: Writes[SesameAssignmentPlan] = SesameAssignmentPlan.writes

  override implicit def jsonWritesAtom: Writes[SesameAssignmentPlanAtom] = SesameAssignmentPlan.writesAtom

  def entries(s: String): Set[SesameAssignmentEntry] = (0 until 5).map(n =>
    SesameAssignmentEntry(n, s"${n.toString} to $s", SesameAssignmentEntryType.all)
  ).toSet

  override val mimeType: LwmMimeType = LwmMimeType.assignmentPlanV1Json

  import bindings.AssignmentPlanDescriptor
  import ops._

  implicit val assignmentPlanBinder = AssignmentPlanDescriptor.binder
  override val pointedGraph: PointedGraph[Sesame] = entityToPass.toPG

  override val updateJson: JsValue = Json.obj(
    "labwork" -> entityToPass.labwork,
    "attendance" -> entityToPass.attendance,
    "mandatory" -> entityToPass.mandatory,
    "entries" -> Json.toJson(entityToPass.entries.drop(2))
  )

  override val inputJson: JsValue = Json.obj(
    "labwork" -> entityToPass.labwork,
    "attendance" -> entityToPass.attendance,
    "mandatory" -> entityToPass.mandatory,
    "entries" -> Json.toJson(entityToPass.entries)
  )

  "An AssignmentPlanCRUDControllerSpec also " should {

    "return all assignment plans for a given labwork" in {
      val labwork = UUID.randomUUID
      val ap1 = SesameAssignmentPlan(labwork, 0, 0, entries("ap1"))
      val ap2 = SesameAssignmentPlan(UUID.randomUUID, 0, 0, entries("ap2"))
      val ap3 = SesameAssignmentPlan(labwork, 0, 0, entries("ap3"))
      val ap4 = SesameAssignmentPlan(UUID.randomUUID, 0, 0, entries("ap4"))

      when(repository.getAll[SesameAssignmentPlan](anyObject())).thenReturn(Success(Set(ap1, ap2, ap3, ap4)))

      val request = FakeRequest(
        GET,
        s"/${entityTypeName.toLowerCase}s?${AssignmentPlanCRUDController.labworkAttribute}=$labwork"
      )
      val result = controller.all()(request)
      val expected = Set(Json.toJson(ap1), Json.toJson(ap3))

      status(result) shouldBe OK
      contentType(result) shouldBe Some(mimeType.value)
      contentFromStream(result) shouldBe expected
    }

    "not return all assignment plans for a given labwork when there is an exception" in {
      val labwork = UUID.randomUUID
      val errorMessage = "Oops, something went wrong"

      when(repository.getAll[SesameAssignmentPlan](anyObject())).thenReturn(Failure(new Throwable(errorMessage)))

      val request = FakeRequest(
        GET,
        s"/${entityTypeName.toLowerCase}s?${AssignmentPlanCRUDController.labworkAttribute}=$labwork"
      )
      val result = controller.all()(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "errors" -> errorMessage
      )
    }

    "return all assignment plans for a given course" in {
      val course = UUID.randomUUID
      val lab1 = SesameLabwork("", "", UUID.randomUUID, course, UUID.randomUUID)
      val lab2 = SesameLabwork("", "", UUID.randomUUID, course, UUID.randomUUID)
      val ap1 = SesameAssignmentPlan(lab1.id, 0, 0, entries("ap1"))
      val ap2 = SesameAssignmentPlan(UUID.randomUUID, 0, 0, entries("ap2"))
      val ap3 = SesameAssignmentPlan(UUID.randomUUID, 0, 0, entries("ap3"))
      val ap4 = SesameAssignmentPlan(lab1.id, 0, 0, entries("ap4"))
      val ap5 = SesameAssignmentPlan(lab2.id, 0, 0, entries("ap5"))
      val ap6 = SesameAssignmentPlan(UUID.randomUUID, 0, 0, entries("ap6"))
      val ap7 = SesameAssignmentPlan(lab2.id, 0, 0, entries("ap7"))
      val ap8 = SesameAssignmentPlan(lab1.id, 0, 0, entries("ap8"))

      when(repository.getAll[SesameAssignmentPlan](anyObject())).thenReturn(Success(Set(
        ap1, ap2, ap3, ap4, ap5, ap6, ap7, ap8
      )))
      when(repository.getMany[SesameLabwork](anyObject())(anyObject())).thenReturn(Success(Set(lab1, lab2)))

      val request = FakeRequest(
        GET,
        s"/${entityTypeName.toLowerCase}s?${AssignmentPlanCRUDController.courseAttribute}=$course"
      )
      val result = controller.all()(request)
      val expected = Set(ap1, ap4, ap5, ap7, ap8) map (e => Json.toJson(e))

      status(result) shouldBe OK
      contentType(result) shouldBe Some(mimeType.value)
      contentFromStream(result) shouldBe expected
    }

    "return an empty json when there are no assignment plans for a given course" in {
      val course = UUID.randomUUID
      val lab1 = SesameLabwork("", "", UUID.randomUUID, course, UUID.randomUUID)
      val lab2 = SesameLabwork("", "", UUID.randomUUID, course, UUID.randomUUID)
      val aps = (0 until 1).map(i => SesameAssignmentPlan(UUID.randomUUID, 0, 0, entries(i.toString))).toSet

      when(repository.getAll[SesameAssignmentPlan](anyObject())).thenReturn(Success(aps))
      when(repository.getMany[SesameLabwork](anyObject())(anyObject())).thenReturn(Success(Set(lab1, lab2)))

      val request = FakeRequest(
        GET,
        s"/${entityTypeName.toLowerCase}s?${AssignmentPlanCRUDController.courseAttribute}=$course"
      )
      val result = controller.all()(request)

      status(result) shouldBe OK
      contentType(result) shouldBe Some(mimeType.value)
      contentFromStream(result) shouldBe emptyJson
    }

    "not return assignment plans when there is an invalid query attribute" in {
      val aps = (0 until 1).map(i => SesameAssignmentPlan(UUID.randomUUID, 0, 0, entries(i.toString))).toSet

      when(repository.getAll[SesameAssignmentPlan](anyObject())).thenReturn(Success(aps))

      val request = FakeRequest(
        GET,
        s"/${entityTypeName.toLowerCase}s?invalidAttribute=${UUID.randomUUID}"
      )
      val result = controller.all()(request)

      status(result) shouldBe SERVICE_UNAVAILABLE
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "Unknown attribute"
      )
    }

    s"handle this model issue when creating a new assignmentplan which already exists" in {
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.execute(anyObject())).thenReturn(Success(Map(
        "s" -> List(factory.createLiteral(SesameAssignmentPlan.generateUri(entityToPass)))
      )))
      when(repository.get[SesameAssignmentPlan](anyObject())(anyObject())).thenReturn(Success(Some(entityToPass)))

      val request = FakeRequest(
        POST,
        s"/${entityTypeName}s",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> mimeType)),
        inputJson
      )
      val result = controller.create()(request)

      status(result) shouldBe ACCEPTED
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "model already exists",
        "id" -> entityToPass.id
      )
    }

    s"neither create or update an existing assignmentplan when resource does not exists although body would lead to duplication" in {
      doReturn(Success(None)).doReturn(Success(Some(entityToPass))).when(repository).get(anyObject())(anyObject())
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.execute(anyObject())).thenReturn(Success(Map(
        "s" -> List(factory.createLiteral(SesameAssignmentPlan.generateUri(entityToPass)))
      )))

      val request = FakeRequest(
        PUT,
        s"/${entityTypeName}s/${entityToPass.id}",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> mimeType)),
        updateJson
      )
      val result = controller.update(entityToPass.id.toString)(request)

      status(result) shouldBe ACCEPTED
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "model already exists",
        "id" -> entityToPass.id
      )
    }
  }
}