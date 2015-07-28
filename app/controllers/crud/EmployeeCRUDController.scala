package controllers.crud

import java.util.UUID

import models.UriGenerator
import models.users.{EmployeeProtocol, Employee}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import store.{Namespace, SesameRepository}
import utils.LWMMimeType

import scala.collection.Map

class EmployeeCRUDController(val repository: SesameRepository, val namespace: Namespace) extends AbstractCRUDController[EmployeeProtocol, Employee] {
   override implicit def rdfWrites: ToPG[Sesame, Employee] = defaultBindings.EmployeeBinding.employeeBinder

   override implicit def rdfReads: FromPG[Sesame, Employee] = defaultBindings.EmployeeBinding.employeeBinder

   override implicit def classUrisFor: ClassUrisFor[Sesame, Employee] = defaultBindings.EmployeeBinding.classUri

   override implicit def uriGenerator: UriGenerator[Employee] = Employee

   override implicit def reads: Reads[EmployeeProtocol] = Employee.reads

   override implicit def writes: Writes[Employee] = Employee.writes

   override def getWithFilter(queryString: Map[String, Seq[String]]): Result = ???

   override protected def fromInput(input: EmployeeProtocol, id: Option[UUID]): Employee = ???

   override def mimeType: LWMMimeType = LWMMimeType.employeeV1Json
}
