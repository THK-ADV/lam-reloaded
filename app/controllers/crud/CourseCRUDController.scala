package controllers.crud

import java.util.UUID

import models.users.Employee
import models.{Course, CourseAtom, CourseProtocol, UriGenerator}
import org.w3.banana.RDFPrefix
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json._
import services.RoleService
import store.Prefixes.LWMPrefix
import store.sparql.SelectClause
import store.{Namespace, SesameRepository}
import utils.LwmMimeType
import models.security.Permissions._
import models.security.{Authority, RefRole, Role}
import models.security.Roles._
import scala.collection.Map
import scala.util.{Failure, Try}
import store.sparql.select
import store.sparql.select._

object CourseCRUDController {
  val lecturerAttribute = "lecturer"
}

class CourseCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[CourseProtocol, Course] {
  override implicit def rdfWrites: ToPG[Sesame, Course] = defaultBindings.CourseBinding.courseBinder

  override implicit def rdfReads: FromPG[Sesame, Course] = defaultBindings.CourseBinding.courseBinder

  override implicit def classUrisFor: ClassUrisFor[Sesame, Course] = defaultBindings.CourseBinding.classUri

  override implicit def uriGenerator: UriGenerator[Course] = Course

  override implicit def reads: Reads[CourseProtocol] = Course.reads

  override implicit def writes: Writes[Course] = Course.writes

  override protected def fromInput(input: CourseProtocol, id: Option[UUID]): Course = id match {
    case Some(uuid) => Course(input.label, input.description, input.abbreviation, input.lecturer, input.semesterIndex, uuid)
    case None => Course(input.label, input.description, input.abbreviation, input.lecturer, input.semesterIndex, Course.randomUUID)
  }

  override protected def atomize(output: Course): Try[Option[JsValue]] = {
    import utils.Ops._
    import utils.Ops.MonadInstances.{tryM, optM}
    import defaultBindings.EmployeeBinding.employeeBinder
    import Course.atomicWrites

    repository.get[Employee](Employee.generateUri(output.lecturer)(namespace)).peek { employee =>
      val atom = CourseAtom(output.label, output.description, output.abbreviation, employee, output.semesterIndex, output.id)
      Json.toJson(atom)
    }
  }

  override protected def atomizeMany(output: Set[Course]): Try[JsValue] = {
    import defaultBindings.EmployeeBinding.employeeBinder
    import Course.atomicWrites

    (for {
      employees <- repository.getMany[Employee](output.map(c => Employee.generateUri(c.lecturer)(namespace)))
    } yield {
      output.foldLeft(Set.empty[CourseAtom]) { (newSet, c) =>
        employees.find(_.id == c.lecturer) match {
          case Some(employee) =>
            val atom = CourseAtom(c.label, c.description, c.abbreviation, employee, c.semesterIndex, c.id)
            newSet + atom
          case None =>
            newSet
        }
      }
    }).map(s => Json.toJson(s))
  }

  override val mimeType: LwmMimeType = LwmMimeType.courseV1Json

  override def getWithFilter(queryString: Map[String, Seq[String]])(courses: Set[Course]): Try[Set[Course]] = {
    import CourseCRUDController._

    queryString.foldRight(Try[Set[Course]](courses)) {
      case ((`lecturerAttribute`, v), t) => t flatMap (set => Try(UUID.fromString(v.head)).map(p => set.filter(_.lecturer == p)))
      case ((_, _), set) => Failure(new Throwable("Unknown attribute"))
    }
  }

  override protected def existsQuery(input: CourseProtocol): (SelectClause, Var) = {
    lazy val prefixes = LWMPrefix[repository.Rdf]
    lazy val rdf = RDFPrefix[repository.Rdf]

    (select ("id") where {
      ^(v("s"), p(rdf.`type`), s(prefixes.Course)) .
        ^(v("s"), p(prefixes.label), o(input.label)) .
        ^(v("s"), p(prefixes.description), o(input.description)) .
        ^(v("s"), p(prefixes.id), v("id"))
    }, v("id"))
  }

  def updateFrom(course: String) = restrictedContext(course)(Update) asyncContentTypedAction { request =>
    super.update(course, NonSecureBlock)(request)
  }

  def updateAtomicFrom(course: String) = restrictedContext(course)(Update) asyncContentTypedAction { request =>
    super.updateAtomic(course, NonSecureBlock)(request)
  }

  def createWithRights(secureContext: SecureContext = contextFrom(Create)) = secureContext contentTypedAction { request =>
    request.body.validate[CourseProtocol].fold(
      errors => {
        BadRequest(Json.obj(
          "status" -> "KO",
          "errors" -> JsError.toJson(errors)
        ))
      },
      success => handleExistance(success) { model =>
        import defaultBindings.CourseBinding._
        import defaultBindings.RoleBinding
        import defaultBindings.RefRoleBinding._
        import defaultBindings.AuthorityBinding._
          for {
            allRoles <- repository.get[Role](RoleBinding.roleBinder, RoleBinding.classUri)
            properRoles = allRoles filter (role => (role.name == CourseManager) && (role.name == CourseEmployee) && (role.name == Assistant)) if properRoles.nonEmpty
            authrole = allRoles filter (_.name == RightsManager) if authrole.nonEmpty
            refroles = properRoles map (role => RefRole(Some(model.id), role.id))
            authority = Authority(model.lecturer, (refroles ++ authrole) map (_.id))
            _ <- repository.add[Course](model)
            _ <- repository.addMany[RefRole](refroles)
            _ <- repository.add[Authority](authority)
          } yield Created(Json.toJson(model)).as(mimeType)
        }
      )
  }

  def createAtomicWithRights(secureContext: SecureContext = contextFrom(Create)) = secureContext contentTypedAction { request =>
    request.body.validate[CourseProtocol].fold(
      errors => {
        BadRequest(Json.obj(
          "status" -> "KO",
          "errors" -> JsError.toJson(errors)
        ))
      },
      success => handleExistance(success) { model =>
        import defaultBindings.CourseBinding._
        import defaultBindings.RoleBinding
        import defaultBindings.RefRoleBinding._
        import defaultBindings.AuthorityBinding._
        for {
          allRoles <- repository.get[Role](RoleBinding.roleBinder, RoleBinding.classUri)
          properRoles = allRoles filter (role => (role.name == CourseManager) && (role.name == CourseEmployee) && (role.name == Assistant)) if properRoles.nonEmpty
          authrole = allRoles filter (_.name == RightsManager) if authrole.nonEmpty
          refroles = properRoles map (role => RefRole(Some(model.id), role.id))
          authority = Authority(model.lecturer, (refroles ++ authrole) map (_.id))
          _ <- repository.add[Course](model)
          _ <- repository.addMany[RefRole](refroles)
          _ <- repository.add[Authority](authority)
          atomized <- atomize(model)
        } yield atomized match {
          case Some(json) =>
            Created(json).as(mimeType)
          case None =>
            NotFound(Json.obj(
              "status" -> "KO",
              "message" -> "No such element..."
            ))
        }
      }
    )
  }

  override protected def compareModel(input: CourseProtocol, output: Course): Boolean = {
    input.label == output.label && input.description == output.description
  }

  override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
    case Get => PartialSecureBlock(course.get)
    case GetAll => PartialSecureBlock(course.getAll)
    case _ => PartialSecureBlock(prime)
  }

  override protected def restrictedContext(restrictionId: String): PartialFunction[Rule, SecureContext] = {
    case Update => SecureBlock(restrictionId, course.update)
    case _ => PartialSecureBlock(god)
  }
}
