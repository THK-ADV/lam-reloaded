package controllers.reportCard

import java.util.UUID

import controllers.crud._
import models.{Room, UriGenerator}
import models.labwork.{ReportCardEntryAtom, Labwork, ReportCardEntry}
import models.users.{Student, User}
import modules.store.BaseNamespace
import org.openrdf.model.Value
import org.w3.banana.RDFPrefix
import org.w3.banana.binder.{FromPG, ClassUrisFor, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json._
import play.api.mvc.{Result, Controller}
import services.{RoleService, SessionHandlingService}
import store.Prefixes.LWMPrefix
import store.{Namespace, SesameRepository}
import utils.LwmMimeType
import models.security.Permissions._

import scala.collection.Map
import scala.util.{Try, Failure, Success}

object ReportCardEntryController {
  val studentAttribute = "student"
  val labworkAttribute = "labwork"
}

class ReportCardEntryController(val repository: SesameRepository, val sessionService: SessionHandlingService, val namespace: Namespace, val roleService: RoleService) extends Controller
  with BaseNamespace
  with JsonSerialisation[ReportCardEntry, ReportCardEntry]
  with SesameRdfSerialisation[ReportCardEntry]
  with ContentTyped
  with Secured
  with SessionChecking
  with SecureControllerContext
  with Atomic[ReportCardEntry] {

  override implicit def reads: Reads[ReportCardEntry] = ReportCardEntry.reads

  override implicit def writes: Writes[ReportCardEntry] = ReportCardEntry.writes

  override implicit def rdfReads: FromPG[Sesame, ReportCardEntry] = defaultBindings.ReportCardEntryBinding.reportCardEntryBinding

  override implicit def classUrisFor: ClassUrisFor[Sesame, ReportCardEntry] = defaultBindings.ReportCardEntryBinding.classUri

  override implicit def uriGenerator: UriGenerator[ReportCardEntry] = ReportCardEntry

  override implicit def rdfWrites: ToPG[Sesame, ReportCardEntry] = defaultBindings.ReportCardEntryBinding.reportCardEntryBinding

  override implicit val mimeType: LwmMimeType = LwmMimeType.reportCardEntryV1Json

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
    case Get => PartialSecureBlock(reportCardEntry.get)
    case _ => PartialSecureBlock(god)
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Update => SecureBlock(restrictionId, reportCardEntry.update)
    case GetAll => SecureBlock(restrictionId, reportCardEntry.getAll)
    case _ => PartialSecureBlock(god)
  }

  def update(course: String, entry: String) = restrictedContext(course)(Update) contentTypedAction { request =>
    request.body.validate[ReportCardEntry].fold(
      errors => {
        BadRequest(Json.obj(
          "status" -> "KO",
          "errors" -> JsError.toJson(errors)
        ))
      },
      success => {
        success.id == UUID.fromString(entry) match {
          case true =>
            repository.update(success) match {
              case Success(_) =>
                Ok(Json.toJson(success)).as(mimeType)
              case Failure(e) =>
                InternalServerError(Json.obj(
                  "status" -> "KO",
                  "errors" -> e.getMessage
                ))
            }
          case false =>
            BadRequest(Json.obj(
              "status" -> "KO",
              "message" -> s"Update body does not match with ReportCardEntry (${success.id})"
            ))
        }
      }
    )
  }

  def get(student: String) = forStudent(student) { entries =>
    Success(Ok(Json.toJson(entries)).as(mimeType))
  }

  def getAtomic(student: String) = forStudent(student) { entries =>
    atomizeMany(entries).map(json => Ok(json).as(mimeType))
  }

  def all(course: String) = reportCardEntries(course) { entries =>
    Success(Ok(Json.toJson(entries)).as(mimeType))
  }

  def allAtomic(course: String) = reportCardEntries(course) { entries =>
    atomizeMany(entries).map(json => Ok(Json.toJson(json)).as(mimeType))
  }

  private def reportCardEntries(course: String)(f: Set[ReportCardEntry] => Try[Result]) = restrictedContext(course)(GetAll) action { request =>
    import store.sparql.select._
    import store.sparql.select
    import ReportCardEntryController._

    val lwm = LWMPrefix[repository.Rdf]
    val rdf = RDFPrefix[repository.Rdf]
    implicit val ns = repository.namespace

    if (request.queryString.isEmpty)
      BadRequest(Json.obj(
        "status" -> "KO",
        "message" -> "Request should contain at least one attribute"
      ))
    else
      request.queryString.foldLeft(Try(^(v("entries"), p(rdf.`type`), s(lwm.ReportCardEntry)))) {
        case (clause, (`studentAttribute`, values)) => clause map {
          _ append ^(v("entries"), p(lwm.student), s(User.generateUri(UUID.fromString(values.head))))
        }
        case (clause, (`labworkAttribute`, values)) => clause map {
          _ append ^(v("entries"), p(lwm.labwork), s(Labwork.generateUri(UUID.fromString(values.head))))
        }
        case _ => Failure(new Throwable("Unknown attribute"))
      } flatMap { clause =>
        val query = select distinct "entries" where clause

        repository.prepareQuery(query).
          select(_.get("entries")).
          transform(_.fold(List.empty[Value])(identity)).
          requestAll[Set, ReportCardEntry](values => repository.getMany(values.map(_.stringValue))).
          run
      } flatMap f match {
        case Success(result) => result
        case Failure(e) =>
          InternalServerError(Json.obj(
            "status" -> "KO",
            "errors" -> e.getMessage
          ))
      }
  }

  private def forStudent(student: String)(f: Set[ReportCardEntry] => Try[Result]) = contextFrom(Get) action { request =>
    import store.sparql.select
    import store.sparql.select._

    lazy val lwm = LWMPrefix[repository.Rdf]
    lazy val rdf = RDFPrefix[repository.Rdf]

    val query = select ("entries") where {
      ^(v("entries"), p(rdf.`type`), s(lwm.ReportCardEntry)).
        ^(v("entries"), p(lwm.student), s(User.generateUri(UUID.fromString(student))(namespace)))
    }

    repository.prepareQuery(query).
      select(_.get("entries")).
      transform(_.fold(List.empty[Value])(identity)).
      requestAll[Set, ReportCardEntry](values => repository.getMany(values.map(_.stringValue))).run.
      flatMap(f) match {
        case Success(entries) =>
          entries
        case Failure(e) =>
          InternalServerError(Json.obj(
            "status" -> "KO",
            "errors" -> e.getMessage
          ))
      }
  }

  override protected def atomizeMany(output: Set[ReportCardEntry]): Try[JsValue] = {
    import defaultBindings.StudentBinding.studentBinder
    import defaultBindings.LabworkBinding.labworkBinder
    import defaultBindings.RoomBinding.roomBinder
    import ReportCardEntry.atomicWrites

    (for {
      students <- repository.getMany[Student](output.map(e => User.generateUri(e.student)(namespace)))
      labworks <- repository.getMany[Labwork](output.map(e => Labwork.generateUri(e.labwork)(namespace)))
      rooms <- repository.getMany[Room](output.map(e => Room.generateUri(e.room)(namespace)))
    } yield output.foldLeft(Set.empty[ReportCardEntryAtom]) { (set, o) =>
      (for {
        s <- students.find(_.id == o.student)
        l <- labworks.find(_.id == o.labwork)
        r <- rooms.find(_.id == o.room)
      } yield ReportCardEntryAtom(s, l, o.label, o.date, o.start, o.end, r, o.entryTypes, o.rescheduled, o.id)) match {
        case Some(atom) => set + atom
        case None => set
      }
    }).map(set => Json.toJson(set))
  }

  override protected def atomize(output: ReportCardEntry): Try[Option[JsValue]] = Success(Some(Json.toJson(output)))
}