package controllers

import java.util.UUID

import dao.{AuthorityDao, RoleDao}
import database.{RoleDb, RoleTable}
import javax.inject.{Inject, Singleton}
import models.Role.{God, RightsManager}
import models._
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.ControllerComponents
import security.SecurityActionChain

import scala.util.{Failure, Try}

object RoleController {
  lazy val labelAttribute = "label"
}

@Singleton
final class RoleController @Inject()(cc: ControllerComponents, val abstractDao: RoleDao, val authorityDao: AuthorityDao, val securedAction: SecurityActionChain)
  extends AbstractCRUDController[Role, RoleTable, RoleDb, Role](cc) {

  override protected implicit val writes: Writes[Role] = Role.writes

  override protected implicit val reads: Reads[Role] = Role.reads

  override protected def makeTableFilter(attribute: String, value: String): Try[TableFilterPredicate] = {
    import RoleController._

    (attribute, value) match {
      case (`labelAttribute`, l) => l.makeLabelEqualsFilter
      case _ => Failure(new Throwable(s"Unknown attribute $attribute"))
    }
  }

  override protected def toDbModel(protocol: Role, existingId: Option[UUID]): RoleDb = ???

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
    case Get => PartialSecureBlock(List(RightsManager))
    case GetAll => PartialSecureBlock(List(RightsManager))
    case _ => PartialSecureBlock(List(God))
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = forbiddenAction()
}
