package controllers.crud

import java.util.UUID

import models.security.Permission
import models.{Labwork, LabworkProtocol, UriGenerator}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import services.RoleService
import store.{Namespace, SesameRepository}
import utils.LwmMimeType

import scala.collection.Map

object LabworkCRUDController {
   val all = Set[Permission]()

   val createPerm = "createPerm"
   val viewPerm = "viewPerm"
   val editPerm = "editPerm"
   val deletePerm = "deletePerm"
}

class LabworkCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[LabworkProtocol, Labwork] {
   override implicit def rdfWrites: ToPG[Sesame, Labwork] = defaultBindings.LabworkBinding.labworkBinder

   override implicit def rdfReads: FromPG[Sesame, Labwork] = defaultBindings.LabworkBinding.labworkBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Labwork] = defaultBindings.LabworkBinding.classUri

   override implicit def uriGenerator: UriGenerator[Labwork] = Labwork

   override implicit def reads: Reads[LabworkProtocol] = Labwork.reads

   override implicit def writes: Writes[Labwork] = Labwork.writes

   override def getWithFilter(queryString: Map[String, Seq[String]]): Result = ???

   override protected def fromInput(input: LabworkProtocol, id: Option[UUID]): Labwork = ???

   override val mimeType: LwmMimeType = LwmMimeType.labworkV1Json

}
