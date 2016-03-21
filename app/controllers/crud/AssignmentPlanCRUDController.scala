package controllers.crud

import java.util.UUID

import models._
import models.security.Permissions
import org.openrdf.model.Value
import org.w3.banana.RDFPrefix
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import services.{RoleService, SessionHandlingService}
import store.Prefixes.LWMPrefix
import store.{Namespace, SesameRepository}
import utils.LwmMimeType
import utils.RequestOps._
import scala.collection.Map
import scala.util.{Failure, Success, Try}
import AssignmentPlanCRUDController._

object AssignmentPlanCRUDController {
  val labworkAttribute = "labwork"
  val courseAttribute = "course"
}

class AssignmentPlanCRUDController(val repository: SesameRepository, val sessionService: SessionHandlingService, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[AssignmentPlanProtocol, AssignmentPlan] {

  override protected def compareModel(input: AssignmentPlanProtocol, output: AssignmentPlan): Boolean = {
    input.attendance == output.attendance && input.mandatory == output.mandatory && input.entries == output.entries
  }

  override implicit def rdfReads: FromPG[Sesame, AssignmentPlan] = defaultBindings.AssignmentPlanBinding.assignmentPlanBinder

  override implicit def classUrisFor: ClassUrisFor[Sesame, AssignmentPlan] = defaultBindings.AssignmentPlanBinding.classUri

  override implicit def uriGenerator: UriGenerator[AssignmentPlan] = AssignmentPlan

  override implicit def rdfWrites: ToPG[Sesame, AssignmentPlan] = defaultBindings.AssignmentPlanBinding.assignmentPlanBinder

  override protected def atomize(output: AssignmentPlan): Try[Option[JsValue]] = Success(Some(Json.toJson(output)))

  override protected def fromInput(input: AssignmentPlanProtocol, existing: Option[AssignmentPlan]): AssignmentPlan = existing match {
    case Some(ap) => AssignmentPlan(input.labwork, input.attendance, input.mandatory, input.entries, ap.id)
    case None => AssignmentPlan(input.labwork, input.attendance, input.mandatory, input.entries, AssignmentPlan.randomUUID)
  }

  override implicit def reads: Reads[AssignmentPlanProtocol] = AssignmentPlan.reads

  override implicit def writes: Writes[AssignmentPlan] = AssignmentPlan.writes

  override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[AssignmentPlan]): Try[Set[AssignmentPlan]] = {
    import defaultBindings.LabworkBinding.labworkBinder
    import utils.Ops.MonadInstances.listM
    import store.sparql.select
    import store.sparql.select._
    lazy val lwm = LWMPrefix[repository.Rdf]
    lazy val rdf = RDFPrefix[repository.Rdf]

    queryString.foldRight(Try[Set[AssignmentPlan]](all)) {
      case ((`labworkAttribute`, values), t) => t flatMap (set => Try(UUID.fromString(values.head)).map(id => set.filter(_.labwork == id)))
      case ((`courseAttribute`, values), t) =>
        val query = select ("labworks") where {
          ^(v("labworks"), p(rdf.`type`), s(lwm.Labwork)).
          ^(v("labworks"), p(lwm.course), s(Course.generateUri(UUID.fromString(values.head))(namespace)))
        }

        repository.prepareQuery(query).
          select(_.get("labworks")).
          transform(_.fold(List.empty[Value])(identity)).
          map(_.stringValue).
          requestAll(repository.getMany[Labwork](_)).
          requestAll[Set, AssignmentPlan](labworks => t.map(_.filter(p => labworks.exists(_.id == p.labwork)))).
          run
      case ((_, _), set) => Failure(new Throwable("Unknown attribute"))
    }
  }

  override implicit val mimeType: LwmMimeType = LwmMimeType.assignmentPlanV1Json

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
    case _ => PartialSecureBlock(Permissions.god)
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Create => SecureBlock(restrictionId, Permissions.assignmentPlan.create)
    case Update => SecureBlock(restrictionId, Permissions.assignmentPlan.update)
    case Delete => SecureBlock(restrictionId, Permissions.assignmentPlan.delete)
    case Get => SecureBlock(restrictionId, Permissions.assignmentPlan.get)
    case GetAll => SecureBlock(restrictionId, Permissions.assignmentPlan.getAll)
  }

  def createFrom(course: String) = restrictedContext(course)(Create) asyncContentTypedAction { implicit request =>
    create(NonSecureBlock)(rebase(AssignmentPlan.generateBase))
  }

  def updateFrom(course: String, assignmentPlan: String) = restrictedContext(course)(Update) asyncContentTypedAction { implicit request =>
    update(assignmentPlan, NonSecureBlock)(rebase(AssignmentPlan.generateBase(UUID.fromString(assignmentPlan))))
  }

  def allFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { implicit request =>
    all(NonSecureBlock)(rebase(AssignmentPlan.generateBase, courseAttribute -> Seq(course)))
  }

  def getFrom(course: String, assignmentPlan: String) = restrictedContext(course)(Get) asyncAction { implicit request =>
    get(assignmentPlan, NonSecureBlock)(rebase(AssignmentPlan.generateBase(UUID.fromString(assignmentPlan))))
  }

  def deleteFrom(course: String, assignmentPlan: String) = restrictedContext(course)(Delete) asyncAction { implicit request =>
    delete(assignmentPlan, NonSecureBlock)(rebase(AssignmentPlan.generateBase(UUID.fromString(assignmentPlan))))
  }
}
