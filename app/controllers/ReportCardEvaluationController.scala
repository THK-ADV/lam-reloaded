package controllers

import java.util.UUID

import auth.UserToken
import dao._
import dao.helper.TableFilter
import database.{ReportCardEvaluationDb, ReportCardEvaluationTable}
import javax.inject.{Inject, Singleton}
import models.Role._
import models._
import org.apache.poi.ss.usermodel.IndexedColors
import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Result}
import security.SecurityActionChain
import service.sheet._
import utils.Ops

import scala.concurrent.Future
import scala.util.{Failure, Try}

object ReportCardEvaluationController {
  lazy val courseAttribute = "course"
  lazy val labworkAttribute = "labwork"
  lazy val studentAttribute = "student"

  /*lazy val labelAttribute = "label" // TODO
  lazy val boolAttribute = "bool"
  lazy val intAttribute = "int"
  lazy val minIntAttribute = "minInt"
  lazy val maxIntAttribute = "maxInt"
  lazy val explicitAttribute = "explicit"*/
}

@Singleton
final class ReportCardEvaluationController @Inject()(
                                                      cc: ControllerComponents,
                                                      val authorityDao: AuthorityDao,
                                                      val abstractDao: ReportCardEvaluationDao,
                                                      val reportCardEntryDao: ReportCardEntryDao,
                                                      val reportCardEvaluationPatternDao: ReportCardEvaluationPatternDao,
                                                      val securedAction: SecurityActionChain
                                                    )
  extends AbstractCRUDController[ReportCardEvaluationProtocol, ReportCardEvaluationTable, ReportCardEvaluationDb, ReportCardEvaluationLike](cc)
    with FileStreamResult {

  import TableFilter.{labworkFilter, userFilter}
  import controllers.ReportCardEvaluationController._
  import service.ReportCardEvaluationService._

  import scala.concurrent.ExecutionContext.Implicits.global

  def get(student: String) = contextFrom(Get) asyncAction { request =>
    all(NonSecureBlock)(request.appending(studentAttribute -> Seq(student)))
  }

  def createFrom(course: String, labwork: String) = restrictedContext(course)(Create) asyncAction { implicit request =>
    (for {
      labworkId <- labwork.uuidF
      patterns <- Ops.when(reportCardEvaluationPatternDao.get(List(labworkFilter(labworkId)), atomic = false))(_.nonEmpty)(() => "no eval pattern defined")
      cards <- reportCardEntryDao.get(List(labworkFilter(labworkId)), atomic = false)
      existing <- abstractDao.get(List(labworkFilter(labworkId)), atomic = false)
      _ <- abstractDao.createOrUpdateMany(evaluateDeltas(cards.toList, patterns.toList, existing.toList))

      atomic = extractAttributes(request.queryString, defaultAtomic = false)._2.atomic
      evaluations <- abstractDao.get(List(labworkFilter(labworkId)), atomic)
    } yield evaluations).jsonResult
  }

  def createForStudent(course: String, labwork: String, student: String) = restrictedContext(course)(Create) asyncAction { _ =>
    for {
      labworkId <- labwork.uuidF
      studentId <- student.uuidF
      existing <- abstractDao.get(List(labworkFilter(labworkId), userFilter(studentId)), atomic = false)
      result <- if (existing.isEmpty)
        abstractDao.createMany(evaluateExplicit(UUID.fromString(student), UUID.fromString(labwork))).map(_.map(_.toUniqueEntity)).jsonResult
      else
        Future.successful(preconditionFailed(s"$student was already evaluated: ${Json.toJson(existing)}. delete those first before continuing with explicit evaluation."))
    } yield result
  }

  def allFrom(course: String, labwork: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    all(NonSecureBlock)(request.appending(courseAttribute -> Seq(course), labworkAttribute -> Seq(labwork)))
  }

  def invalidateFromStudent(course: String, labwork: String, student: String) = restrictedContext(course)(Delete) asyncAction { _ =>
    for {
      labworkId <- labwork.uuidF
      studentId <- student.uuidF
      result <- delete(List(labworkFilter(labworkId), userFilter(studentId)))
    } yield result
  }

  def invalidateFromLabwork(course: String, labwork: String) = restrictedContext(course)(Delete) asyncAction { _ =>
    for {
      labworkId <- labwork.uuidF
      result <- delete(List(labworkFilter(labworkId)))
    } yield result
  }

  def updateFrom(course: String, labwork: String, id: String) = restrictedContext(course)(Update) asyncAction { request =>
    update(id, NonSecureBlock)(request)
  }

  def renderEvaluationSheet(course: String, labwork: String) = restrictedContext(course)(Create) asyncAction { request =>
    import utils.Ops.whenNonEmpty

    for {
      labworkId <- labwork.uuidF
      allEvals <- whenNonEmpty(abstractDao.get(List(labworkFilter(labworkId))))(() => "no evaluations found")
      allEvals0 = allEvals.map(_.asInstanceOf[ReportCardEvaluationAtom])
      labwork = allEvals0.head.labwork
      token = request.userToken if token.isDefined
      sheet = ExaminationOfficeExport.createSheet(allEvals0, labwork, token.get.firstName, token.get.lastName)
    } yield toResult(sheet)
  }

  private def delete(list: List[TableFilterPredicate]): Future[Result] = {
    (for {
      existing <- abstractDao.get(list, atomic = false)
      deleted <- abstractDao.invalidateMany(existing.map(_.id).toList)
    } yield deleted.map(_.toUniqueEntity)).jsonResult
  }

  override protected implicit val writes: Writes[ReportCardEvaluationLike] = ReportCardEvaluationLike.writes

  override protected implicit val reads: Reads[ReportCardEvaluationProtocol] = ReportCardEvaluationProtocol.reads

  override protected def makeTableFilter(attribute: String, value: String): Try[TableFilterPredicate] = {
    import ReportCardEvaluationController._

    (attribute, value) match {
      case (`courseAttribute`, c) => c.makeCourseFilter
      case (`labworkAttribute`, l) => l.makeLabworkFilter
      case (`studentAttribute`, s) => s.makeUserFilter
      case _ => Failure(new Throwable(s"Unknown attribute $attribute"))
    }
  }

  override protected def toDbModel(protocol: ReportCardEvaluationProtocol, existingId: Option[UUID]): ReportCardEvaluationDb = {
    ReportCardEvaluationDb(protocol.student, protocol.labwork, protocol.label, protocol.bool, protocol.int, id = existingId getOrElse UUID.randomUUID)
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Create => SecureBlock(restrictionId, List(CourseManager))
    case Get => SecureBlock(restrictionId, List(CourseManager))
    case Delete => SecureBlock(restrictionId, List(CourseManager))
    case Update => SecureBlock(restrictionId, List(CourseManager))
    case GetAll => SecureBlock(restrictionId, List(CourseManager, CourseEmployee))
    case _ => PartialSecureBlock(List(God))
  }

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
    case Get => PartialSecureBlock(List(StudentRole))
    case _ => PartialSecureBlock(List(God))
  }
}
