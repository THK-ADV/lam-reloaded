package controllers.crud.schedule

import java.util.UUID

import controllers.crud.AbstractCRUDController
import models.{schedule => _, _}
import models.schedule.{Schedule, ScheduleEntry, ScheduleProtocol, Timetable}
import org.openrdf.model.Value
import models.schedule._
import models.users.Employee
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import services._
import store.Prefixes.LWMPrefix
import store.bind.Bindings
import store.{Namespace, SesameRepository}
import utils.LwmMimeType
import models.security.Permissions._

import scala.collection.Map
import scala.util.{Failure, Success, Try}

object ScheduleCRUDController {

  def competitive(labwork: UUID, repository: SesameRepository): Try[Set[ScheduleG]] = {
    scheduleFor(labwork, repository) map { set =>
      set.flatMap(schedule => toScheduleG(schedule, repository))
    }
  }

  def scheduleFor(labwork: UUID, repository: SesameRepository): Try[Set[Schedule]] = {
    lazy val lwm = LWMPrefix[repository.Rdf]
    val bindings = Bindings[repository.Rdf](repository.namespace)

    import store.sparql.select._
    import store.sparql.select
    import bindings.ScheduleBinding._
    import bindings.ScheduleEntryBinding._
    import utils.Ops._
    import TraverseInstances._
    import MonadInstances.{tryM, listM}

    lazy val id = Labwork.generateUri(labwork)(repository.namespace)

    val query = select distinct "schedules" where {
      ^(s(id), p(lwm.course), v("courseid")).
        ^(s(id), p(lwm.semester), v("semester")).
        ^(v("course"), p(lwm.id), v("courseid")).
        ^(v("course"), p(lwm.semesterIndex), v("semesterIndex")).
        ^(v("labwork"), p(lwm.semester), v("semester")).
        ^(v("labwork"), p(lwm.course), v("course2id")).
        ^(v("course2"), p(lwm.id), v("course2id")).
        ^(v("course2"), p(lwm.semesterIndex), v("semesterIndex")).
        ^(v("labwork"), p(lwm.id), v("labworkid")).
        ^(v("schedules"), p(lwm.labwork), v("labworkid"))
    }

      repository.prepareQuery(query).
      select(_.get("schedules")).
      transform(_.fold(List.empty[Value])(identity)).
      map(_.stringValue()).
      requestAll(repository.getMany[Schedule]).
      run
  }

  private def toScheduleG(schedule: Schedule, repository: SesameRepository): Option[ScheduleG] = {
    val bindings = Bindings[repository.Rdf](repository.namespace)
    import bindings.GroupBinding.groupBinder
    import bindings.GroupBinding._

    import utils.Ops._
    import MonadInstances._

    val maybeEntries =
      (schedule.entries flatMap { entry =>
        val group = repository.get[Group](Group.generateUri(entry.group)(repository.namespace)).toOption
        group.peek(g => ScheduleEntryG(entry.start, entry.end, entry.date, entry.room, entry.supervisor, g, entry.id))
      }).sequence


    maybeEntries map (entries => ScheduleG(schedule.labwork, entries.toVector, schedule.id))
  }
}

class ScheduleCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService, val scheduleGenesisService: ScheduleGenesisServiceLike) extends AbstractCRUDController[ScheduleProtocol, Schedule] {

  override implicit def rdfWrites: ToPG[Sesame, Schedule] = defaultBindings.ScheduleBinding.scheduleBinder

  override implicit def rdfReads: FromPG[Sesame, Schedule] = defaultBindings.ScheduleBinding.scheduleBinder

  override implicit def classUrisFor: ClassUrisFor[Sesame, Schedule] = defaultBindings.ScheduleBinding.classUri

  override implicit def uriGenerator: UriGenerator[Schedule] = Schedule

  override protected def fromInput(input: ScheduleProtocol, id: Option[UUID]): Schedule = id match {
    case Some(uuid) => Schedule(input.labwork, input.entries, uuid)
    case None => Schedule(input.labwork, input.entries, Schedule.randomUUID)
  }

  override implicit def reads: Reads[ScheduleProtocol] = Schedule.reads

  override implicit def writes: Writes[Schedule] = Schedule.writes

  override implicit val mimeType: LwmMimeType = LwmMimeType.scheduleV1Json

  override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[Schedule]): Try[Set[Schedule]] = Success(all)

  def createFrom(course: String) = restrictedContext(course)(Create) asyncContentTypedAction { request =>
    super.create(NonSecureBlock)(request)
  }

  def updateFrom(course: String, schedule: String) = restrictedContext(course)(Update) asyncContentTypedAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Schedule.generateBase(UUID.fromString(schedule)))
    super.update(schedule, NonSecureBlock)(newRequest)
  }

  def createAtomicFrom(course: String) = restrictedContext(course)(Create) asyncContentTypedAction { request =>
    super.createAtomic(NonSecureBlock)(request)
  }

  def updateAtomicFrom(course: String, schedule: String) = restrictedContext(course)(Update) asyncContentTypedAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Schedule.generateBase(UUID.fromString(schedule)))
    super.updateAtomic(schedule, NonSecureBlock)(newRequest)
  }

  def allFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    super.all(NonSecureBlock)(request)
  }

  def allAtomicFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    super.allAtomic(NonSecureBlock)(request)
  }

  def getFrom(course: String, schedule: String) = restrictedContext(course)(Get) asyncAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Schedule.generateBase(UUID.fromString(schedule)))
    super.get(schedule, NonSecureBlock)(newRequest)
  }

  def getAtomicFrom(course: String, schedule: String) = restrictedContext(course)(Get) asyncAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Schedule.generateBase(UUID.fromString(schedule)))
    super.getAtomic(schedule, NonSecureBlock)(newRequest)
  }

  def deleteFrom(course: String, schedule: String) = restrictedContext(course)(Delete) asyncAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Schedule.generateBase(UUID.fromString(schedule)))
    super.delete(schedule, NonSecureBlock)(newRequest)
  }

  def preview(course: String, labwork: String) = restrictedContext(course)(Create) action { implicit request =>
    import utils.Ops._
    import MonadInstances.{tryM, optM}

    implicit val gb = defaultBindings.GroupBinding.groupBinder
    implicit val gcu = defaultBindings.GroupBinding.classUri
    implicit val tb = defaultBindings.TimetableBinding.timetableBinder
    implicit val tcu = defaultBindings.TimetableBinding.classUri
    implicit val lb = defaultBindings.LabworkBinding.labworkBinder

    val id = UUID.fromString(labwork)
    val uri = Labwork.generateUri(id)(namespace)

    val gen = for {
      groups <- repository.get[Group].map(_.filter(_.labwork == id))
      timetable <- repository.get[Timetable].map(_.find(_.labwork == id))
      plan <- repository.get[Labwork](uri).peek(_.assignmentPlan)
      comp <- ScheduleCRUDController.competitive(id, repository)
    } yield {
      for {
        t <- timetable if t.entries.nonEmpty
        p <- plan if p.entries.nonEmpty
        g <- if (groups.nonEmpty) Some(groups) else None
      } yield scheduleGenesisService.generate(t, g, p, comp.toVector)._1
    }

    gen match {
      case Success(s) =>
        s match {
          case Some(ss) =>
            val schedule = {
              val entries = ss.elem.entries.map(e => ScheduleEntry(e.start, e.end, e.date, e.room, e.supervisor, e.group.id, e.id)).toSet
              Schedule(ss.elem.labwork, entries, ss.elem.id)
            }

            Ok(Json.obj(
              "status" -> "OK",
              "schedule" -> Json.toJson(schedule),
              "number of conflicts" -> ss.evaluate.err.size // TODO serialize conflicts
            ))
          case None =>
            NotFound(Json.obj(
              "status" -> "KO",
              "message" -> "No such element..."
            ))
        }
      case Failure(e) =>
        InternalServerError(Json.obj(
          "status" -> "KO",
          "errors" -> e.getMessage
        ))
    }
  }

  override protected def compareModel(input: ScheduleProtocol, output: Schedule): Boolean = input.entries == output.entries

  override protected def atomize(output: Schedule): Try[Option[JsValue]] = {
    import defaultBindings.LabworkBinding._
    import defaultBindings.RoomBinding.roomBinder
    import defaultBindings.EmployeeBinding.employeeBinder
    import defaultBindings.GroupBinding.groupBinder
    import Schedule._
    import ScheduleEntry._

    for {
      labwork <- repository.get[Labwork](Labwork.generateUri(output.labwork)(namespace))
      rooms <- repository.getMany[Room](output.entries.map(e => Room.generateUri(e.room)(namespace)))
      supervisors <- repository.getMany[Employee](output.entries.map(e => Employee.generateUri(e.supervisor)(namespace)))
      groups <- repository.getMany[Group](output.entries.map(e => Group.generateUri(e.group)(namespace)))
    } yield {
      labwork.map { l =>
        val entries = output.entries.foldLeft(Set.empty[ScheduleEntryAtom]) { (newSet, e) =>
          (for {
            r <- rooms.find(_.id == e.room)
            s <- supervisors.find(_.id == e.supervisor)
            g <- groups.find(_.id == e.group)
          } yield ScheduleEntryAtom(e.start, e.end, e.date, r, s, g, e.id)) match {
            case Some(atom) => newSet + atom
            case None => newSet
          }
        }
        Json.toJson(ScheduleAtom(l, entries, output.id))(Schedule.atomicWrites)
      }
    }
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Create => SecureBlock(restrictionId, schedule.create)
    case Get => SecureBlock(restrictionId, schedule.get)
    case GetAll => SecureBlock(restrictionId, schedule.getAll)
    case Update => SecureBlock(restrictionId, schedule.update)
    case Delete => SecureBlock(restrictionId, schedule.delete)
  }
}