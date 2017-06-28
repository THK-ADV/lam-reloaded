package controllers

import java.util.UUID

import models._
import org.openrdf.model.Value
import org.w3.banana.RDFPrefix
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import services.{RoleService, RoleServiceLike, SessionHandlingService}
import store.Prefixes.LWMPrefix
import store.bind.Descriptor.Descriptor
import store.sparql.select._
import store.sparql.{Clause, select}
import store.{Namespace, SesameRepository}
import utils.LwmMimeType
import controllers.AssignmentPlanCRUDController._

import scala.util.{Failure, Try}

object AssignmentPlanCRUDController {
  val labworkAttribute = "labwork"
  val courseAttribute = "course"
}

class AssignmentPlanCRUDController(val repository: SesameRepository, val sessionService: SessionHandlingService, implicit val namespace: Namespace, val roleService: RoleServiceLike) extends AbstractCRUDController[SesameAssignmentPlanProtocol, SesameAssignmentPlan, SesameAssignmentPlanAtom] {

  override implicit val mimeType: LwmMimeType = LwmMimeType.assignmentPlanV1Json

  override implicit val descriptor: Descriptor[Sesame, SesameAssignmentPlan] = defaultBindings.AssignmentPlanDescriptor

  override implicit val descriptorAtom: Descriptor[Sesame, SesameAssignmentPlanAtom] = defaultBindings.AssignmentPlanAtomDescriptor

  override implicit val reads: Reads[SesameAssignmentPlanProtocol] = SesameAssignmentPlan.reads

  override implicit val writes: Writes[SesameAssignmentPlan] = SesameAssignmentPlan.writes

  override implicit val writesAtom: Writes[SesameAssignmentPlanAtom] = SesameAssignmentPlan.writesAtom

  override implicit val uriGenerator: UriGenerator[SesameAssignmentPlan] = SesameAssignmentPlan

  override protected def coAtomic(atom: SesameAssignmentPlanAtom): SesameAssignmentPlan = SesameAssignmentPlan(atom.labwork.id, atom.attendance, atom.mandatory, atom.entries, atom.invalidated, atom.id)

  override protected def compareModel(input: SesameAssignmentPlanProtocol, output: SesameAssignmentPlan): Boolean = {
    input.attendance == output.attendance && input.mandatory == output.mandatory && input.entries == output.entries
  }

  override protected def fromInput(input: SesameAssignmentPlanProtocol, existing: Option[SesameAssignmentPlan]): SesameAssignmentPlan = existing match {
    case Some(ap) => SesameAssignmentPlan(input.labwork, input.attendance, input.mandatory, input.entries, ap.invalidated, ap.id)
    case None => SesameAssignmentPlan(input.labwork, input.attendance, input.mandatory, input.entries)
  }

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

  override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[SesameAssignmentPlan]): Try[Set[SesameAssignmentPlan]] = {
    import store.sparql.select
    import store.sparql.select._
    import utils.Ops.MonadInstances.listM
    import defaultBindings.LabworkDescriptor

    lazy val lwm = LWMPrefix[repository.Rdf]
    lazy val rdf = RDFPrefix[repository.Rdf]

    queryString.foldRight(Try[Set[SesameAssignmentPlan]](all)) {
      case ((`labworkAttribute`, values), t) => t flatMap (set => Try(UUID.fromString(values.head)).map(id => set.filter(_.labwork == id)))
      case ((`courseAttribute`, values), t) =>
        val query = select("labworks") where {
          **(v("labworks"), p(rdf.`type`), s(lwm.Labwork)).
            **(v("labworks"), p(lwm.course), s(SesameCourse.generateUri(UUID.fromString(values.head))))
        }

        repository.prepareQuery(query).
          select(_.get("labworks")).
          transform(_.fold(List.empty[Value])(identity)).
          map(_.stringValue).
          requestAll(repository.getMany[SesameLabwork](_)).
          requestAll[Set, SesameAssignmentPlan](labworks => t.map(_.filter(p => labworks.exists(_.id == p.labwork)))).
          run
      case ((_, _), set) => Failure(new Throwable("Unknown attribute"))
    }
  }

  override protected def existsQuery(input: SesameAssignmentPlanProtocol): Clause = {
    lazy val lwm = LWMPrefix[repository.Rdf]
    lazy val rdf = RDFPrefix[repository.Rdf]

    select("s") where {
      **(v("s"), p(rdf.`type`), s(lwm.AssignmentPlan)).
        **(v("s"), p(lwm.labwork), s(SesameLabwork.generateUri(input.labwork)))
    }
  }

  def createFrom(course: String) = restrictedContext(course)(Create) asyncContentTypedAction { implicit request =>
    create(NonSecureBlock)(rebase)
  }

  // TODO createAtomic

  def updateFrom(course: String, assignmentPlan: String) = restrictedContext(course)(Update) asyncContentTypedAction { implicit request =>
    update(assignmentPlan, NonSecureBlock)(rebase(assignmentPlan))
  }

  // TODO allAtomic

  def allFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { implicit request =>
    all(NonSecureBlock)(rebase(courseAttribute -> Seq(course)))
  }

  // TODO getAtomic

  def getFrom(course: String, assignmentPlan: String) = restrictedContext(course)(Get) asyncAction { implicit request =>
    get(assignmentPlan, NonSecureBlock)(rebase(assignmentPlan))
  }

  def deleteFrom(course: String, assignmentPlan: String) = restrictedContext(course)(Delete) asyncAction { implicit request =>
    delete(assignmentPlan, NonSecureBlock)(rebase(assignmentPlan))
  }
}
