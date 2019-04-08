package controllers

import java.util.UUID

import controllers.helper.GroupingStrategyAttributeFilter
import dao._
import database.{GroupDb, ScheduleEntryDb, ScheduleEntryTable}
import javax.inject.{Inject, Singleton}
import models.Role.{CourseAssistant, CourseEmployee, CourseManager}
import models._
import models.genesis.{Conflict, ScheduleGen}
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{AnyContent, ControllerComponents, Request}
import security.SecurityActionChain
import services._
import utils.Gen

import scala.concurrent.Future

object ScheduleEntryController {
  lazy val courseAttribute = "course"
  lazy val labworkAttribute = "labwork"
  lazy val groupAttribute = "group"
  lazy val supervisorAttribute = "supervisor"

  lazy val dateAttribute = "date"
  lazy val startAttribute = "start"
  lazy val endAttribute = "end"
  lazy val sinceAttribute = "since"
  lazy val untilAttribute = "until"

  lazy val popAttribute = "pops"
  lazy val genAttribute = "gens"
  lazy val eliteAttribute = "elites"

  lazy val semesterIndexConsiderationAttribute = "considerSemesterIndex"
}

@Singleton
final class ScheduleEntryController @Inject()(
  cc: ControllerComponents,
  val authorityDao: AuthorityDao,
  val abstractDao: ScheduleEntryDao,
  val scheduleService: ScheduleService,
  val assignmentPlanService: AssignmentPlanDao,
  val labworkService: LabworkDao,
  val timetableService: TimetableDao,
  val labworkApplicationService2: LabworkApplicationDao,
  val groupDao: GroupDao,
  val securedAction: SecurityActionChain
) extends AbstractCRUDController[ScheduleEntryProtocol, ScheduleEntryTable, ScheduleEntryDb, ScheduleEntryLike](cc) with GroupingStrategyAttributeFilter {

  import controllers.ScheduleEntryController._
  import dao.helper.TableFilterable.labworkFilter

  import scala.concurrent.ExecutionContext.Implicits.global

  override protected implicit val writes: Writes[ScheduleEntryLike] = ScheduleEntryLike.writes

  override protected implicit val reads: Reads[ScheduleEntryProtocol] = ScheduleEntryProtocol.reads

  implicit val genWrites: Writes[(Gen[ScheduleGen, Conflict, Int], Int)] = new Writes[(Gen[ScheduleGen, Conflict, Int], Int)] {
    override def writes(gen: (Gen[ScheduleGen, Conflict, Int], Int)) = Json.obj(
      "schedule" -> Json.toJson(gen._1.elem),
      "conflicts" -> Json.toJson(gen._1.evaluate.err),
      "conflict value" -> gen._1.evaluate.value,
      "fitness" -> gen._2
    )
  }

  def createFrom(course: String) = restrictedContext(course)(Create) asyncAction { implicit request =>
    import utils.LwmDateTime.{LocalDateConverter, LocalTimeConverter}

    (for {
      s <- Future.fromTry(parseJson(request)(ScheduleGen.reads))
      labwork = s.labwork
      groups = s.entries.map(_.group).distinct.map(e => GroupDb(e.label, e.labwork, e.members, id = e.id)).toList
      se = s.entries.map(e => database.ScheduleEntryDb(labwork, e.start.sqlTime, e.end.sqlTime, e.date.sqlDate, e.room, e.supervisor, e.group.id)).toList
      _ <- groupDao.createMany(groups)
      _ <- abstractDao.createMany(se)

      atomic = extractAttributes(request.queryString, defaultAtomic = false)._2.atomic
      scheduleEntries <- if (atomic)
        abstractDao.getMany(se.map(_.id), atomic)
      else
        Future.successful(se.map(_.toUniqueEntity))

    } yield scheduleEntries).jsonResult
  }

  def preview(course: String, labwork: String) = restrictedContext(course)(Create) asyncAction { implicit request =>
    labwork.uuidF.flatMap(l => generate(l)).created
  }

  private def generate(labwork: UUID)(implicit request: Request[AnyContent]) = for {
    timetables <- timetableService.withBlacklists(List(labworkFilter(labwork))) if timetables.nonEmpty
    (timetable, blacklists) = {
      val h = timetables.head
      (h._1, h._2.toVector)
    }

    applications <- labworkApplicationService2.get(List(labworkFilter(labwork)), atomic = false)
    apps = applications.map(_.asInstanceOf[LabworkApplication]).toVector

    groupingStrategy <- Future.fromTry(extractGroupingStrategy(request.queryString))
    groups = GroupService.groupApplicantsBy(groupingStrategy, apps, labwork)

    assignmentPlans <- assignmentPlanService.get(List(labworkFilter(labwork)), atomic = false) if assignmentPlans.nonEmpty
    ap = assignmentPlans.head.asInstanceOf[AssignmentPlan]

    lab <- labworkService.getSingle(labwork) if lab.isDefined
    labAtom = lab.get.asInstanceOf[LabworkAtom]
    semester = labAtom.semester

    c = boolOf(request.queryString)(semesterIndexConsiderationAttribute).getOrElse(true)
    comps <- abstractDao.competitive(labAtom, c)

    i = intOf(request.queryString) _
    pop = i(popAttribute)
    gen = i(genAttribute)
    elite = i(eliteAttribute)
  } yield scheduleService.generate(timetable, blacklists, groups, ap, semester, comps, pop, gen, elite)

  def allFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    all(NonSecureBlock)(request.appending(courseAttribute -> Seq(course)))
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Create => SecureBlock(restrictionId, List(CourseManager))
    case Delete => SecureBlock(restrictionId, List(CourseManager))
    case GetAll => SecureBlock(restrictionId, List(CourseManager, CourseEmployee, CourseAssistant))
    case Get => SecureBlock(restrictionId, List(CourseManager, CourseEmployee, CourseAssistant))
    case Update => SecureBlock(restrictionId, List(CourseManager))
  }

  def allFromLabwork(course: String, labwork: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    all(NonSecureBlock)(request.appending(courseAttribute -> Seq(course), labworkAttribute -> Seq(labwork)))
  }

  def getFrom(course: String, id: String) = restrictedContext(course)(Get) asyncAction { request =>
    get(id, NonSecureBlock)(request)
  }

  def updateFrom(course: String, id: String) = restrictedContext(course)(Update) asyncAction { request =>
    update(id, NonSecureBlock)(request)
  }

  def deleteFrom(course: String, labwork: String) = restrictedContext(course)(Delete) asyncAction { request =>
    ???
  }

  //  override protected def tableFilter(attribute: String, value: String)(appendTo: Try[List[TableFilter[ScheduleEntryTable]]]): Try[List[TableFilter[ScheduleEntryTable]]] = {
  //    (appendTo, (attribute, value)) match {
  //      case (list, (`courseAttribute`, course)) => list.map(_.+:(ScheduleEntryCourseFilter(course)))
  //      case (list, (`labworkAttribute`, labwork)) => list.map(_.+:(ScheduleEntryLabworkFilter(labwork)))
  //      case (list, (`groupAttribute`, group)) => list.map(_.+:(ScheduleEntryGroupFilter(group)))
  //      case (list, (`supervisorAttribute`, supervisor)) => list.map(_.+:(ScheduleEntrySupervisorFilter(supervisor)))
  //      case (list, (`dateAttribute`, date)) => list.map(_.+:(ScheduleEntryDateFilter(date)))
  //      case (list, (`startAttribute`, start)) => list.map(_.+:(ScheduleEntryStartFilter(start)))
  //      case (list, (`endAttribute`, end)) => list.map(_.+:(ScheduleEntryEndFilter(end)))
  //      case (list, (`sinceAttribute`, since)) => list.map(_.+:(ScheduleEntrySinceFilter(since)))
  //      case (list, (`untilAttribute`, until)) => list.map(_.+:(ScheduleEntryUntilFilter(until)))
  //      case _ => Failure(new Throwable("Unknown attribute"))
  //    }
  //  }

  override protected def toDbModel(protocol: ScheduleEntryProtocol, existingId: Option[UUID]): ScheduleEntryDb = ???

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = forbidden()
}
