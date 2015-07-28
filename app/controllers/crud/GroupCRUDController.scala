package controllers.crud

import java.util.UUID

import models.{GroupProtocol, Group, UriGenerator}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import store.{Namespace, SesameRepository}
import utils.LWMMimeType

import scala.collection.Map

class GroupCRUDController(val repository: SesameRepository, val namespace: Namespace) extends AbstractCRUDController[GroupProtocol, Group] {
   override implicit def rdfWrites: ToPG[Sesame, Group] = defaultBindings.GroupBinding.groupBinder

   override implicit def rdfReads: FromPG[Sesame, Group] = defaultBindings.GroupBinding.groupBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Group] = defaultBindings.GroupBinding.classUri

   override implicit def uriGenerator: UriGenerator[Group] = Group

   override implicit def reads: Reads[GroupProtocol] = Group.reads

   override implicit def writes: Writes[Group] = Group.writes

   override def getWithFilter(queryString: Map[String, Seq[String]]): Result = ???

   override protected def fromInput(input: GroupProtocol, id: Option[UUID]): Group = ???

   override def mimeType: LWMMimeType = LWMMimeType.groupV1Json
}
