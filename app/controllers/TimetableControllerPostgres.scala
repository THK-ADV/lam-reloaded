package controllers

import java.util.UUID

import dao._
import javax.inject.{Inject, Singleton}
import models.Role.{CourseAssistant, CourseEmployee, CourseManager}
import models.{PostgresTimetableProtocol, Timetable, TimetableDb}
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.ControllerComponents
import store.{TableFilter, TimetableTable}
import utils.SecuredAction

import scala.util.{Failure, Try}

object TimetableControllerPostgres {
  lazy val courseAttribute = "course"
  lazy val labworkAttribute = "labwork"
}

@Singleton
final class TimetableControllerPostgres @Inject()(cc: ControllerComponents, val authorityDao: AuthorityDao, val abstractDao: TimetableDao, val securedAction: SecuredAction)
  extends AbstractCRUDControllerPostgres[PostgresTimetableProtocol, TimetableTable, TimetableDb, Timetable](cc) {

  override protected implicit val writes: Writes[Timetable] = Timetable.writes

  override protected implicit val reads: Reads[PostgresTimetableProtocol] = PostgresTimetableProtocol.reads

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Create => SecureBlock(restrictionId, List(CourseManager))
    case Get => SecureBlock(restrictionId, List(CourseManager, CourseEmployee, CourseAssistant))
    case GetAll => SecureBlock(restrictionId, List(CourseManager, CourseEmployee))
    case Update => SecureBlock(restrictionId, List(CourseManager))
    case Delete => SecureBlock(restrictionId, List(CourseManager))
  }

  def createFrom(course: String) = restrictedContext(course)(Create) asyncAction { request =>
    create(NonSecureBlock)(request)
  }

  def updateFrom(course: String, id: String) = restrictedContext(course)(Update) asyncAction { request =>
    update(id, NonSecureBlock)(request)
  }

  def deleteFrom(course: String, id: String) = restrictedContext(course)(Delete) asyncAction { request =>
    delete(id, NonSecureBlock)(request)
  }

  def allFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    import controllers.TimetableControllerPostgres.courseAttribute

    all(NonSecureBlock)(request.append(courseAttribute -> Seq(course)))
  }

  def getFrom(course: String, id: String) = restrictedContext(course)(Get) asyncAction { request =>
    get(id, NonSecureBlock)(request)
  }

  override protected def tableFilter(attribute: String, value: String)(appendTo: Try[List[TableFilter[TimetableTable]]]): Try[List[TableFilter[TimetableTable]]] = {
    import controllers.TimetableControllerPostgres._

    (appendTo, (attribute, value)) match {
      case (list, (`courseAttribute`, course)) => list.map(_.+:(TimetableCourseFilter(course)))
      case (list, (`labworkAttribute`, labwork)) => list.map(_.+:(TimetableLabworkFilter(labwork)))
      case _ => Failure(new Throwable("Unknown attribute"))
    }
  }

  override protected def toDbModel(protocol: PostgresTimetableProtocol, existingId: Option[UUID]): TimetableDb = TimetableDb.from(protocol, existingId)

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = forbidden()
}
