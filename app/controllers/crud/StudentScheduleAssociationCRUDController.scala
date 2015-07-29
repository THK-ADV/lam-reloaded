package controllers.crud

import java.util.UUID

import models.UriGenerator
import models.schedules.{StudentScheduleAssociationProtocol, StudentScheduleAssociation}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import store.{Namespace, SesameRepository}
import utils.LWMMimeType

import scala.collection.Map

class StudentScheduleAssociationCRUDController(val repository: SesameRepository, val namespace: Namespace) extends AbstractCRUDController[StudentScheduleAssociationProtocol, StudentScheduleAssociation] {
   override implicit def rdfWrites: ToPG[Sesame, StudentScheduleAssociation] = defaultBindings.StudentScheduleAssociationBinding.studentScheduleAssociationBinder

   override implicit def rdfReads: FromPG[Sesame, StudentScheduleAssociation] = defaultBindings.StudentScheduleAssociationBinding.studentScheduleAssociationBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, StudentScheduleAssociation] = defaultBindings.StudentScheduleAssociationBinding.classUri

   override implicit def uriGenerator: UriGenerator[StudentScheduleAssociation] = StudentScheduleAssociation

   override implicit def reads: Reads[StudentScheduleAssociationProtocol] = StudentScheduleAssociation.reads

   override implicit def writes: Writes[StudentScheduleAssociation] = StudentScheduleAssociation.writes

   override def getWithFilter(queryString: Map[String, Seq[String]]): Result = ???

   override protected def fromInput(input: StudentScheduleAssociationProtocol, id: Option[UUID]): StudentScheduleAssociation = ???

   override val mimeType: LWMMimeType = LWMMimeType.studentScheduleAssociationV1Json
}
