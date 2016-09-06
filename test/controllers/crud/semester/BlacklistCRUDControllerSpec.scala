package controllers.crud.semester

import base.StreamHandler._
import controllers.crud.AbstractCRUDControllerSpec
import models.semester.{Blacklist, BlacklistProtocol, Semester}
import org.joda.time.{DateTime, Interval}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.w3.banana.PointedGraph
import org.w3.banana.sesame.Sesame
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers._
import utils.LwmMimeType

import scala.util.Success

class BlacklistCRUDControllerSpec extends AbstractCRUDControllerSpec[BlacklistProtocol, Blacklist, Blacklist] {

  import ops._
  import bindings.BlacklistDescriptor

  val dates = (0 until 10).map(DateTime.now.plusWeeks).toSet

  override def entityTypeName: String = "blacklist"

  override val controller: BlacklistCRUDController = new BlacklistCRUDController(repository, sessionService, namespace, roleService) {

    override protected def fromInput(input: BlacklistProtocol, existing: Option[Blacklist]): Blacklist = entityToPass

    override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
      case _ => NonSecureBlock
    }
  }

  override val entityToFail: Blacklist = Blacklist("blacklist to fail", dates)

  override val entityToPass: Blacklist = Blacklist("blacklist to pass", dates)

  override implicit val jsonWrites: Writes[Blacklist] = Blacklist.writes

  override val atomizedEntityToPass: Blacklist = entityToPass

  override val atomizedEntityToFail: Blacklist = entityToFail

  override val jsonWritesAtom: Writes[Blacklist] = jsonWrites

  implicit val blacklistBinder = BlacklistDescriptor.binder

  override val pointedGraph: PointedGraph[Sesame] = entityToPass.toPG

  override val mimeType: LwmMimeType = LwmMimeType.blacklistV1Json

  override val inputJson: JsValue = Json.obj(
    "label" -> entityToPass.label,
    "dates" -> entityToPass.dates
  )

  override val updateJson: JsValue = Json.obj(
    "label" -> entityToPass.label,
    "dates" -> (entityToPass.dates + DateTime.now)
  )

  "A BlacklistCRUDControllerSpec also " should {

    "create blacklists for a given year" in {
      val year = DateTime.now.getYear

      when(repository.add[Blacklist](anyObject())(anyObject())).thenReturn(Success(pointedGraph))

      val request = FakeRequest(
        POST,
        s"/${entityTypeName.toLowerCase}/year/$year",
        FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> mimeType)),
        Json.obj("" -> "")
      )

      val result = controller.createFor(year.toString)(request)

      status(result) shouldBe CREATED
      contentType(result) shouldBe Some[String](mimeType)
      contentFromStream(result) should not be empty
    }

    "return all blacklists in current semester" in {
      val semesters = SemesterCRUDControllerSpec.populate
      val blacklists = (0 until 10).map { i =>
        import scala.util.Random._
        Blacklist(i.toString, (0 until 10).map(_ => DateTime.now.withMonthOfYear(nextInt(11) + 1).withDayOfMonth(nextInt(27) + 1).plusYears(if (nextBoolean) nextInt(2) + 1 * 1 else nextInt(2) + 1 * -1)).toSet)
      }.toSet

     doReturn(Success(semesters)).doReturn(Success(blacklists)).when(repository).getAll(anyObject())

      val request = FakeRequest(
        GET,
        s"/${entityTypeName.toLowerCase}/current"
      )

      val result = controller.allCurrent()(request)
      val currentSemester = Semester.findCurrent(semesters)
      val currentBlacklists = currentSemester.map { semester =>
        blacklists.foldLeft(Set.empty[Blacklist]) {
          case (set, bl) =>
            val dates = bl.dates.filter(date => new Interval(semester.start.toDateTimeAtCurrentTime, semester.end.toDateTimeAtCurrentTime).contains(date))
            if (dates.nonEmpty) set + bl.copy(bl.label, dates) else set
        }
      }

      status(result) shouldBe OK
      contentType(result) shouldBe Some[String](mimeType)
      contentFromStream(result) shouldBe currentBlacklists.fold(Set.empty[JsValue])(set => set.map(s => Json.toJson(s)))
    }
  }
}
