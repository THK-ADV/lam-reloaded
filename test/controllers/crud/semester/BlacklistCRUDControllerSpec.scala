package controllers.crud.semester

import java.util.UUID

import controllers.crud.{AbstractCRUDController, AbstractCRUDControllerSpec}
import models.semester.{Blacklist, BlacklistProtocol}
import org.joda.time.DateTime
import org.w3.banana.PointedGraph
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Json, Writes, JsValue}
import utils.LwmMimeType

class BlacklistCRUDControllerSpec extends AbstractCRUDControllerSpec[BlacklistProtocol, Blacklist] {

  import ops._
  import bindings.BlacklistBinding.blacklistBinder

  val dates = (0 until 10).map(DateTime.now.plusWeeks).toSet

  override def entityTypeName: String = "blacklist"

  override val controller: AbstractCRUDController[BlacklistProtocol, Blacklist] = new BlacklistCRUDController(repository, namespace, roleService) {

    override protected def fromInput(input: BlacklistProtocol, id: Option[UUID]): Blacklist = entityToPass

    override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
      case _ => NonSecureBlock
    }

    override protected def duplicate(input: BlacklistProtocol, output: Blacklist): Boolean = true
  }

  override val entityToFail: Blacklist = Blacklist(dates, Blacklist.randomUUID)

  override val entityToPass: Blacklist = Blacklist(dates, Blacklist.randomUUID)

  override val pointedGraph: PointedGraph[Sesame] = entityToPass.toPG

  override implicit val jsonWrites: Writes[Blacklist] = Blacklist.writes

  override val mimeType: LwmMimeType = LwmMimeType.blacklistV1Json

  override val inputJson: JsValue = Json.obj(
    "dates" -> entityToPass.dates
  )
}
