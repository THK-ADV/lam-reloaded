package controllers.crud

import java.util.UUID

import models._
import models.users.Student
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json._
import services.{GroupServiceLike, RoleService}
import store.{Namespace, SesameRepository}
import utils.LwmMimeType
import models.security.Permissions._
import scala.collection.Map
import scala.util.{Success, Failure, Try}

object GroupCRUDController {
  val labworkAttribute = "labwork"
}

class GroupCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService, val groupService: GroupServiceLike) extends AbstractCRUDController[GroupProtocol, Group] {

  override val mimeType: LwmMimeType = LwmMimeType.groupV1Json

  override implicit def rdfWrites: ToPG[Sesame, Group] = defaultBindings.GroupBinding.groupBinder

  override implicit def classUrisFor: ClassUrisFor[Sesame, Group] = defaultBindings.GroupBinding.classUri

  override implicit def uriGenerator: UriGenerator[Group] = Group

  override implicit def reads: Reads[GroupProtocol] = Group.reads

  override implicit def writes: Writes[Group] = Group.writes

  def updateFrom(course: String, group: String) = restrictedContext(course)(Update) asyncContentTypedAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Group.generateBase(UUID.fromString(group)))
    super.update(group, NonSecureBlock)(newRequest)
  }

  def allFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    super.all(NonSecureBlock)(request)
  }

  def allAtomicFrom(course: String) = restrictedContext(course)(GetAll) asyncAction { request =>
    super.allAtomic(NonSecureBlock)(request)
  }

  def getFrom(course: String, group: String) = restrictedContext(course)(Get) asyncAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Group.generateBase(UUID.fromString(group)))
    super.get(group, NonSecureBlock)(newRequest)
  }

  def getAtomicFrom(course: String, group: String) = restrictedContext(course)(Get) asyncAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Group.generateBase(UUID.fromString(group)))
    super.getAtomic(group, NonSecureBlock)(newRequest)
  }

  def deleteFrom(course: String, group: String) = restrictedContext(course)(Delete) asyncAction { request =>
    val newRequest = AbstractCRUDController.rebaseUri(request, Group.generateBase(UUID.fromString(group)))
    super.delete(group, NonSecureBlock)(newRequest)
  }

  def createWithRange(course: String) = restrictedContext(course)(Create) contentTypedAction { implicit request =>
    request.body.validate[GroupRangeProtocol].fold(
      errors => {
        BadRequest(Json.obj(
          "status" -> "KO",
          "errors" -> JsError.toJson(errors)
        ))
      },
      success => {
        def size(min: Int, max: Int, s: Int): Int = ((min to max) reduce { (prev, curr) =>
          if (prev % s < curr % s) curr
          else prev
        }) + 1

        val processed =
          for {
            people <- groupService.sortApplicantsFor(success.labwork) if people.nonEmpty
            groupSize = size(success.min, success.max, people.size)
            grouped = people.grouped(groupSize).toList
            zipped = groupService.alphabeticalOrdering(grouped.size) zip grouped
            mapped = zipped map (t => Group(t._1, success.labwork, t._2.toSet))
            _ <- repository.addMany[Group](mapped)
          } yield mapped

        processed match {
          case Success(groups) =>
            Ok(Json.toJson(groups)).as(mimeType)
          case Failure(e) =>
            InternalServerError(Json.obj(
              "status" -> "KO",
              "errors" -> s"Error while creating groups for labwork ${success.labwork}: ${e.getMessage}"
            ))
        }
      }
    )
  }

  def createWithCount(course: String) = restrictedContext(course)(Create) contentTypedAction { implicit request =>
    request.body.validate[GroupCountProtocol].fold(
      errors => {
        BadRequest(Json.obj(
          "status" -> "KO",
          "errors" -> JsError.toJson(errors)
        ))
      },
      success => {
        val processed =
          for {
            people <- groupService.sortApplicantsFor(success.labwork) if people.nonEmpty
            groupSize = (people.size / success.count) + 1
            grouped = people.grouped(groupSize).toList
            zipped = groupService.alphabeticalOrdering(grouped.size) zip grouped
            mapped = zipped map (t => Group(t._1, success.labwork, t._2.toSet))
            _ <- repository.addMany[Group](mapped)
          } yield mapped

        processed match {
          case Success(groups) =>
            Ok(Json.toJson(groups)).as(mimeType)
          case Failure(e) =>
            InternalServerError(Json.obj(
              "status" -> "KO",
              "errors" -> s"Error while creating groups for labwork ${success.labwork}: ${e.getMessage}"
            ))
        }
      }
    )
  }

  override implicit def rdfReads: FromPG[Sesame, Group] = defaultBindings.GroupBinding.groupBinder

  override protected def fromInput(input: GroupProtocol, id: Option[UUID]): Group = id match {
    case Some(uuid) => Group(input.label, input.labwork, input.members, uuid)
    case None => Group(input.label, input.labwork, input.members, Group.randomUUID)
  }

  override protected def compareModel(input: GroupProtocol, output: Group): Boolean = {
    input.label == output.label && input.labwork == output.labwork && input.members == output.members
  }

  override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[Group]): Try[Set[Group]] = {
    import GroupCRUDController._

    queryString.foldRight(Try[Set[Group]](all)) {
      case ((`labworkAttribute`, v), t) => t flatMap (set => Try(UUID.fromString(v.head)).map(p => set.filter(_.labwork == p)))
      case ((_, _), set) => Failure(new Throwable("Unknown attribute"))
    }
  }

  override protected def atomize(output: Group): Try[Option[JsValue]] = {
    import defaultBindings.LabworkBinding._
    import defaultBindings.StudentBinding._
    import Group.atomicWrites

    for {
      labwork <- repository.get[Labwork](Labwork.generateUri(output.labwork)(namespace))
      students <- repository.getMany[Student](output.members.map(id => Student.generateUri(id)(namespace)))
    } yield {
      labwork.map { l =>
        val atom = GroupAtom(output.label, l, students, output.id)
        Json.toJson(atom)
      }
    }
  }

  override protected def atomizeMany(output: Set[Group]): Try[JsValue] = {
    import defaultBindings.LabworkBinding._
    import defaultBindings.StudentBinding._
    import Group.atomicWrites
    import utils.Ops._
    import utils.Ops.MonadInstances.tryM

    (for {
      labworks <- repository.getMany[Labwork](output.map(g => Labwork.generateUri(g.labwork)(namespace)))
      students <- output.map(g => repository.getMany[Student](g.members.map(id => Student.generateUri(id)(namespace)))).sequence
    } yield {
      output.foldLeft(Set.empty[GroupAtom]) { (newSet, g) =>
        (for {
          l <- labworks.find(_.id == g.labwork)
          ss <- students.find(_.map(_.id) == g.members)
        } yield GroupAtom(g.label, l, ss, g.id)) match {
          case Some(atom) => newSet + atom
          case None => newSet
        }
      }
    }).map(s => Json.toJson(s))
  }

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
    case Get => PartialSecureBlock(group.get)
    case _ => PartialSecureBlock(god)
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Create => SecureBlock(restrictionId, group.create)
    case Update => SecureBlock(restrictionId, group.update)
    case Delete => SecureBlock(restrictionId, group.delete)
    case Get => SecureBlock(restrictionId, group.get)
    case GetAll => SecureBlock(restrictionId, group.getAll)
  }
}
