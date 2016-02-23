package controllers.crud.user

import java.util.UUID

import controllers.crud.AbstractCRUDController
import models.UriGenerator
import models.users.{Student, StudentProtocol}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import services.RoleService
import store.{Namespace, SesameRepository}
import utils.LwmMimeType

import scala.collection.Map

class StudentCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[StudentProtocol, Student] {
   override implicit def rdfWrites: ToPG[Sesame, Student] = defaultBindings.StudentBinding.studentBinder

   override implicit def rdfReads: FromPG[Sesame, Student] = defaultBindings.StudentBinding.studentBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Student] = defaultBindings.StudentBinding.classUri

   override implicit def uriGenerator: UriGenerator[Student] = Student

   override implicit def reads: Reads[StudentProtocol] = Student.reads

   override implicit def writes: Writes[Student] = Student.writes

   override protected def fromInput(input: StudentProtocol, id: Option[UUID]): Student = ???

   override def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[Student]): Result = ???

   override val mimeType: LwmMimeType = LwmMimeType.studentV1Json

   override protected def compareModel(input: StudentProtocol, output: Student): Boolean = {
      input.systemId == output.systemId && input.email == output.email && input.firstname == output.firstname && input.lastname == output.lastname && input.registrationId == output.registrationId
   }
}
