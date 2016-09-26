package controllers.crud.labwork

import java.util.UUID

import controllers.crud.AbstractCRUDController
import controllers.crud.labwork.AssignmentPlanCRUDController._
import models._
import models.labwork.{AssignmentPlan, AssignmentPlanAtom, AssignmentPlanProtocol, Labwork}
import models.security.Permissions
import org.openrdf.model.Value
import org.w3.banana.RDFPrefix
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{Reads, Writes}
import services.{RoleService, SessionHandlingService}
import store.Prefixes.LWMPrefix
import store.bind.Descriptor.Descriptor
import store.sparql.select._
import store.sparql.{Clause, select}
import store.{Namespace, SesameRepository}
import utils.LwmMimeType

import scala.collection.Map
import scala.util.{Failure, Try}

object AssignmentPlanCRUDController {
  val labworkAttribute = "labwork"
  val courseAttribute = "course"
}

class AssignmentPlanCRUDController(val repository: SesameRepository, val sessionService: SessionHandlingService, implicit val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[AssignmentPlanProtocol, AssignmentPlan, AssignmentPlanAtom] {

  override implicit val mimeType: LwmMimeType = LwmMimeType.assignmentPlanV1Json

  override implicit val descriptor: Descriptor[Sesame, AssignmentPlan] = defaultBindings.AssignmentPlanDescriptor

  override implicit val descriptorAtom: Descriptor[Sesame, AssignmentPlanAtom] = defaultBindings.AssignmentPlanAtomDescriptor

  override implicit val reads: Reads[AssignmentPlanProtocol] = AssignmentPlan.reads

  override implicit val writes: Writes[AssignmentPlan] = AssignmentPlan.writes

  override implicit val writesAtom: Writes[AssignmentPlanAtom] = AssignmentPlan.writesAtom

  override implicit val uriGenerator: UriGenerator[AssignmentPlan] = AssignmentPlan

  override protected def coAtomic(atom: AssignmentPlanAtom): AssignmentPlan = AssignmentPlan(atom.labwork.id, atom.attendance, atom.mandatory, atom.entries, atom.invalidated, atom.id)

  override protected def compareModel(input: AssignmentPlanProtocol, output: AssignmentPlan): Boolean = {
    input.attendance == output.attendance && input.mandatory == output.mandatory && input.entries == output.entries
  }

  override protected def fromInput(input: AssignmentPlanProtocol, existing: Option[AssignmentPlan]): AssignmentPlan = existing match {
    case Some(ap) => AssignmentPlan(input.labwork, input.attendance, input.mandatory, input.entries, ap.invalidated, ap.id)
    case None => AssignmentPlan(input.labwork, input.attendance, input.mandatory, input.entries)
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

  override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[AssignmentPlan]): Try[Set[AssignmentPlan]] = {
    import store.sparql.select
    import store.sparql.select._
    import utils.Ops.MonadInstances.listM
    import defaultBindings.LabworkDescriptor

    lazy val lwm = LWMPrefix[repository.Rdf]
    lazy val rdf = RDFPrefix[repository.Rdf]

    queryString.foldRight(Try[Set[AssignmentPlan]](all)) {
      case ((`labworkAttribute`, values), t) => t flatMap (set => Try(UUID.fromString(values.head)).map(id => set.filter(_.labwork == id)))
      case ((`courseAttribute`, values), t) =>
        val query = select("labworks") where {
          **(v("labworks"), p(rdf.`type`), s(lwm.Labwork)).
            **(v("labworks"), p(lwm.course), s(Course.generateUri(UUID.fromString(values.head))))
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

  override protected def existsQuery(input: AssignmentPlanProtocol): Clause = {
    lazy val lwm = LWMPrefix[repository.Rdf]
    lazy val rdf = RDFPrefix[repository.Rdf]

    select("s") where {
      **(v("s"), p(rdf.`type`), s(lwm.AssignmentPlan)).
        **(v("s"), p(lwm.labwork), s(Labwork.generateUri(input.labwork)))
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
