package controllers.crud

import java.util.UUID

import models.UriGenerator
import models.users.{StudentProtocol, Student}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import store.{Namespace, SesameRepository}
import utils.LWMMimeType

import scala.collection.Map

class StudentCRUDController(val repository: SesameRepository, val namespace: Namespace) extends AbstractCRUDController[StudentProtocol, Student] {
   override implicit def rdfWrites: ToPG[Sesame, Student] = defaultBindings.StudentBinding.studentBinder

   override implicit def rdfReads: FromPG[Sesame, Student] = defaultBindings.StudentBinding.studentBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Student] = defaultBindings.StudentBinding.classUri

   override implicit def uriGenerator: UriGenerator[Student] = Student

   override implicit def reads: Reads[StudentProtocol] = Student.reads

   override implicit def writes: Writes[Student] = Student.writes

   override def getWithFilter(queryString: Map[String, Seq[String]]): Result = ???

   override protected def fromInput(input: StudentProtocol, id: Option[UUID]): Student = ???

   override val mimeType: LWMMimeType = LWMMimeType.studentV1Json
}
