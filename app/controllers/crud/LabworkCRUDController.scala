package controllers.crud

import java.util.UUID

import models.{LabworkProtocol, Labwork, UriGenerator}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import store.{Namespace, SesameRepository}
import utils.LWMMimeType

import scala.collection.Map

class LabworkCRUDController(val repository: SesameRepository, val namespace: Namespace) extends AbstractCRUDController[LabworkProtocol, Labwork] {
   override implicit def rdfWrites: ToPG[Sesame, Labwork] = defaultBindings.LabworkBinding.labworkBinder

   override implicit def rdfReads: FromPG[Sesame, Labwork] = defaultBindings.LabworkBinding.labworkBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Labwork] = defaultBindings.LabworkBinding.classUri

   override implicit def uriGenerator: UriGenerator[Labwork] = Labwork

   override implicit def reads: Reads[LabworkProtocol] = Labwork.reads

   override implicit def writes: Writes[Labwork] = Labwork.writes

   override def getWithFilter(queryString: Map[String, Seq[String]]): Result = ???

   override protected def fromInput(input: LabworkProtocol, id: Option[UUID]): Labwork = ???

   override val mimeType: LWMMimeType = LWMMimeType.labworkV1Json
}
