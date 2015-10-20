package controllers.crud

import java.util.UUID

import models.UriGenerator
import models.schedules.{GroupScheduleAssociation, GroupScheduleAssociationProtocol}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import services.RoleService
import store.{Namespace, SesameRepository}
import utils.LwmMimeType

import scala.collection.Map

class GroupScheduleAssociationCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[GroupScheduleAssociationProtocol, GroupScheduleAssociation] {
   override implicit def rdfWrites: ToPG[Sesame, GroupScheduleAssociation] = defaultBindings.GroupScheduleAssociationBinding.groupScheduleAssociationBinder

   override implicit def rdfReads: FromPG[Sesame, GroupScheduleAssociation] = defaultBindings.GroupScheduleAssociationBinding.groupScheduleAssociationBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, GroupScheduleAssociation] = defaultBindings.GroupScheduleAssociationBinding.classUri

   override implicit def uriGenerator: UriGenerator[GroupScheduleAssociation] = GroupScheduleAssociation

   override implicit def reads: Reads[GroupScheduleAssociationProtocol] = GroupScheduleAssociation.reads

   override implicit def writes: Writes[GroupScheduleAssociation] = GroupScheduleAssociation.writes

   override protected def fromInput(input: GroupScheduleAssociationProtocol, id: Option[UUID]): GroupScheduleAssociation = ???

   override def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[GroupScheduleAssociation]): Result = ???

   override val mimeType: LwmMimeType = LwmMimeType.groupScheduleAssociationV1Json
}
