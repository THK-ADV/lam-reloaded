package controllers.crud.user

import java.util.UUID

import controllers.crud.AbstractCRUDController
import models.UriGenerator
import models.users.{Employee, EmployeeProtocol}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import services.RoleService
import store.{Namespace, SesameRepository}
import utils.LwmMimeType

import scala.collection.Map

class EmployeeCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[EmployeeProtocol, Employee] {
   override implicit def rdfWrites: ToPG[Sesame, Employee] = defaultBindings.EmployeeBinding.employeeBinder

   override implicit def rdfReads: FromPG[Sesame, Employee] = defaultBindings.EmployeeBinding.employeeBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Employee] = defaultBindings.EmployeeBinding.classUri

   override implicit def uriGenerator: UriGenerator[Employee] = Employee

   override implicit def reads: Reads[EmployeeProtocol] = Employee.reads

   override implicit def writes: Writes[Employee] = Employee.writes

   override protected def fromInput(input: EmployeeProtocol, id: Option[UUID]): Employee = ???

   override def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[Employee]): Result = ???

   override val mimeType: LwmMimeType = LwmMimeType.employeeV1Json

   override protected def compareModel(input: EmployeeProtocol, output: Employee): Boolean = {
      input.systemId == output.systemId && input.email == output.email && input.firstname == output.firstname && input.lastname == output.lastname
   }
}