package controllers

import java.util.UUID

import dao._
import javax.inject.{Inject, Singleton}
import models.Role.{Admin, Employee}
import models.{BlacklistDb, PostgresBlacklist, PostgresBlacklistProtocol}
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.ControllerComponents
import services.blacklist.BlacklistService
import store.{BlacklistTable, TableFilter}
import utils.SecuredAction

import scala.concurrent.Future
import scala.util.{Failure, Try}

object BlacklistControllerPostgres {
  lazy val globalAttribute = "global"
  lazy val labelAttribute = "label"

  lazy val dateAttribute = "date"
  lazy val startAttribute = "start"
  lazy val endAttribute = "end"
  lazy val sinceAttribute = "since"
  lazy val untilAttribute = "until"
}

@Singleton
final class BlacklistControllerPostgres @Inject()(cc: ControllerComponents, val authorityDao: AuthorityDao, val abstractDao: BlacklistDao, val blacklistService: BlacklistService, val securedAction: SecuredAction)
  extends AbstractCRUDControllerPostgres[PostgresBlacklistProtocol, BlacklistTable, BlacklistDb, PostgresBlacklist](cc) {

  import scala.concurrent.ExecutionContext.Implicits.global

  override protected implicit val writes: Writes[PostgresBlacklist] = PostgresBlacklist.writes

  override protected implicit val reads: Reads[PostgresBlacklistProtocol] = PostgresBlacklistProtocol.reads

  def createFor(year: String) = contextFrom(Create) asyncAction { implicit request =>
    import utils.Ops.unwrapTrys

    (for {
      blacklists <- fetch(year)
      partialCreated <- abstractDao.createManyPartial(blacklists)
      (succeeded, failed) = unwrapTrys(partialCreated)
    } yield (blacklists.map(_.toLwmModel), succeeded.map(_.toLwmModel), failed)).jsonResult
  }

  def preview(year: String) = contextFrom(Create) asyncAction { _ =>
    fetch(year).map(_.map(_.toLwmModel)).jsonResult
  }

  private def fetch(year: String) = for {
    year <- Future.fromTry(Try(year.toInt))
    blacklists <- blacklistService.fetchLegalHolidays(year)
  } yield blacklists

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
    case Get => PartialSecureBlock(List(Employee))
    case _ => PartialSecureBlock(List(Admin))
  }

  override protected def tableFilter(attribute: String, value: String)(appendTo: Try[List[TableFilter[BlacklistTable]]]): Try[List[TableFilter[BlacklistTable]]] = {
    import controllers.BlacklistControllerPostgres._

    (appendTo, (attribute, value)) match {
      case (list, (`globalAttribute`, global)) => list.map(_.+:(BlacklistGlobalFilter(global)))
      case (list, (`labelAttribute`, label)) => list.map(_.+:(BlacklistLabelFilter(label)))
      case (list, (`dateAttribute`, date)) => list.map(_.+:(BlacklistDateFilter(date)))
      case (list, (`startAttribute`, start)) => list.map(_.+:(BlacklistStartFilter(start)))
      case (list, (`endAttribute`, end)) => list.map(_.+:(BlacklistEndFilter(end)))
      case (list, (`sinceAttribute`, since)) => list.map(_.+:(BlacklistSinceFilter(since)))
      case (list, (`untilAttribute`, until)) => list.map(_.+:(BlacklistUntilFilter(until)))
      case _ => Failure(new Throwable("Unknown attribute"))
    }
  }

  override protected def toDbModel(protocol: PostgresBlacklistProtocol, existingId: Option[UUID]): BlacklistDb = BlacklistDb.from(protocol, existingId)

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = forbidden()
}
