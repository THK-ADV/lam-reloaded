package controllers.crud.security

import java.util.UUID

import controllers.crud.AbstractCRUDController
import models.UriGenerator
import models.security.{Role, RoleProtocol}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Json, JsValue, Reads, Writes}
import play.api.mvc.Result
import services.RoleService
import store.{Namespace, SesameRepository}
import utils.LwmMimeType

import scala.collection.Map
import scala.util.{Success, Try}

class RoleCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[RoleProtocol, Role] {

  override implicit def reads: Reads[RoleProtocol] = Role.reads

  override implicit def writes: Writes[Role] = Role.writes

  override implicit def rdfReads: FromPG[Sesame, Role] = defaultBindings.RoleBinding.roleBinder

  override implicit def classUrisFor: ClassUrisFor[Sesame, Role] = defaultBindings.RoleBinding.classUri

  override implicit def uriGenerator: UriGenerator[Role] = Role

  override implicit def rdfWrites: ToPG[Sesame, Role] = defaultBindings.RoleBinding.roleBinder

  override implicit val mimeType: LwmMimeType = LwmMimeType.roleV1Json

  override protected def fromInput(input: RoleProtocol, id: Option[UUID]): Role = id match {
    case Some(x) => Role(input.name, input.permissions, x)
    case None => Role(input.name, input.permissions)
  }

  override protected def compareModel(input: RoleProtocol, output: Role): Boolean = {
    input.name == output.name && input.permissions == output.permissions
  }

  override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[Role]): Try[Set[Role]] = Success(all)

  override protected def atomize(output: Role): Try[Option[JsValue]] = Success(Some(Json.toJson(output)))

  override protected def atomizeMany(output: Set[Role]): Try[JsValue] = Success(Json.toJson(output))
}
