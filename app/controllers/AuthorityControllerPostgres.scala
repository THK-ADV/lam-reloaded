package controllers

import java.util.UUID

import dao._
import models.Role._
import models._
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.{Action, AnyContent}
import store.{AuthorityTable, TableFilter}

import scala.concurrent.Future
import scala.util.{Failure, Try}

//object AuthorityControllerPostgres {
//  lazy val userAttribute = "user"
//  lazy val courseAttribute = "course"
//  lazy val roleAttribute = "role"
//  lazy val roleLabelAttribute = "roleLabel"
//}
//
//final class AuthorityControllerPostgres(val abstractDao: AuthorityDao) extends AbstractCRUDControllerPostgres[PostgresAuthorityProtocol, AuthorityTable, AuthorityDb, Authority] {
//
//  import scala.concurrent.ExecutionContext.Implicits.global
//
//  override protected implicit val writes: Writes[Authority] = Authority.writes
//
//  override protected implicit val reads: Reads[PostgresAuthorityProtocol] = PostgresAuthorityProtocol.reads
//
//  override implicit val authorityDao: AuthorityDao = abstractDao
//
//  override def delete(id: String, secureContext: SecureContext): Action[AnyContent] = contextFrom(Delete) asyncAction { _ =>
//    val uuid = UUID.fromString(id)
//
//    for {
//      auth <- abstractDao.getById(id)
//      student = Role.Student.label
//      employee = Role.Employee.label
//      isBasicRole = auth.map(_.asInstanceOf[PostgresAuthorityAtom]).exists(a => a.role.label == student || a.role.label == employee)
//      result <- if (!isBasicRole)
//        delete0(uuid)
//      else
//        Future.successful(preconditionFailed(s"The user associated with $id have to remain with at least one basic role, namely $student or $employee"))
//    } yield result
//  }
//
//  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
//    case Create => PartialSecureBlock(List(RightsManager))
//    case Delete => PartialSecureBlock(List(RightsManager))
//    case GetAll => PartialSecureBlock(List(RightsManager, Student, Employee))
//    case Get => PartialSecureBlock(List(RightsManager))
//    case _ => PartialSecureBlock(List(God))
//  }
//
//
//  override protected def tableFilter(attribute: String, value: String)(appendTo: Try[List[TableFilter[AuthorityTable]]]): Try[List[TableFilter[AuthorityTable]]] = {
//    import controllers.AuthorityControllerPostgres._
//
//    (appendTo, (attribute, value)) match {
//      case (list, (`userAttribute`, user)) => list.map(_.+:(AuthorityUserFilter(user)))
//      case (list, (`courseAttribute`, course)) => list.map(_.+:(AuthorityCourseFilter(course)))
//      case (list, (`roleAttribute`, role)) => list.map(_.+:(AuthorityRoleFilter(role)))
//      case (list, (`roleLabelAttribute`, role)) => list.map(_.+:(AuthorityRoleLabelFilter(role)))
//      case _ => Failure(new Throwable("Unknown attribute"))
//    }
//  }
//
//  override protected def toDbModel(protocol: PostgresAuthorityProtocol, existingId: Option[UUID]): AuthorityDb = AuthorityDb(
//    protocol.user, protocol.role, protocol.course, id = existingId.getOrElse(UUID.randomUUID)
//  )
//}
