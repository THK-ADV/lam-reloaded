package controllers.crud.user

import java.util.UUID

import controllers.crud.AbstractCRUDController
import models.{Degree, UriGenerator}
import models.users.{StudentAtom, Student, StudentProtocol}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Json, JsValue, Reads, Writes}
import services.RoleService
import store.{Namespace, SesameRepository}
import utils.LwmMimeType

import scala.collection.Map
import scala.util.{Success, Try}

class StudentCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[StudentProtocol, Student] {
   override implicit def rdfWrites: ToPG[Sesame, Student] = defaultBindings.StudentBinding.studentBinder

   override implicit def rdfReads: FromPG[Sesame, Student] = defaultBindings.StudentBinding.studentBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Student] = defaultBindings.StudentBinding.classUri

   override implicit def uriGenerator: UriGenerator[Student] = Student

   override implicit def reads: Reads[StudentProtocol] = Student.reads

   override implicit def writes: Writes[Student] = Student.writes

   override protected def fromInput(input: StudentProtocol, id: Option[UUID]): Student = id match {
      case Some(uuid) =>
         Student(input.systemId, input.lastname, input.firstname, input.email, input.registrationId, input.enrollment, uuid)
      case None =>
         Student(input.systemId, input.lastname, input.firstname, input.email, input.registrationId, input.enrollment, Student.randomUUID)
   }

   override val mimeType: LwmMimeType = LwmMimeType.studentV1Json

   override protected def compareModel(input: StudentProtocol, output: Student): Boolean = {
      input.systemId == output.systemId && input.email == output.email && input.firstname == output.firstname && input.lastname == output.lastname && input.registrationId == output.registrationId
   }

   override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[Student]): Try[Set[Student]] = Success(all)

   override protected def atomize(output: Student): Try[Option[JsValue]] = {
      import utils.Ops._
      import utils.Ops.MonadInstances.{tryM, optM}
      import defaultBindings.DegreeBinding.degreeBinder
      import Student.atomicWrites

      repository.get[Degree](Degree.generateUri(output.enrollment)(namespace)).peek { degree =>
         val atom = StudentAtom(output.systemId, output.lastname, output.firstname, output.email, output.registrationId, degree, output.id)
         Json.toJson(atom)
      }
   }

   override protected def atomizeMany(output: Set[Student]): Try[JsValue] = {
      import defaultBindings.DegreeBinding.degreeBinder
      import Student.atomicWrites

      (for {
         degrees <- repository.getMany[Degree](output.map(s => Degree.generateUri(s.enrollment)(namespace)))
      } yield {
         output.foldLeft(Set.empty[StudentAtom]) { (newSet, s) =>
            degrees.find(_.id == s.enrollment) match {
               case Some(degree) =>
                  val atom = StudentAtom(s.systemId, s.lastname, s.firstname, s.email, s.registrationId, degree, s.id)
                  newSet + atom
               case None =>
                  newSet
            }
         }
      }).map(s => Json.toJson(s))
   }
}
