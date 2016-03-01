package controllers.crud

import java.net.URLDecoder
import java.util.UUID
import models.users.Student
import models.{Labwork, UriGenerator}
import models.applications.{LabworkApplicationAtom, LabworkApplication, LabworkApplicationProtocol}
import org.joda.time.DateTime
import org.w3.banana.binder.{FromPG, ClassUrisFor, ToPG}
import org.w3.banana.sesame.Sesame
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc.Result
import services.RoleService
import store.{Namespace, SesameRepository}
import utils.LwmMimeType
import LabworkApplicationCRUDController._
import scala.collection.Map
import scala.util.{Try, Failure, Success}

object LabworkApplicationCRUDController {
  val labworkAttribute = "labwork"
  val applicantAttribute = "applicant"
  val friendAttribute = "friend"
  val dateAttribute = "date"
  val minTime = "minTime"
  val maxTime = "maxTime"
}

//DateTime format is: yyyy-MM-dd'T'HH:mm
class LabworkApplicationCRUDController(val repository: SesameRepository, val namespace: Namespace, val roleService: RoleService) extends AbstractCRUDController[LabworkApplicationProtocol, LabworkApplication] {

  override implicit def reads: Reads[LabworkApplicationProtocol] = LabworkApplication.reads

  override implicit def writes: Writes[LabworkApplication] = LabworkApplication.writes

  override protected def fromInput(input: LabworkApplicationProtocol, id: Option[UUID]): LabworkApplication = id match {
    case Some(uuid) => LabworkApplication(input.labwork, input.applicant, input.friends, DateTime.now, uuid)
    case None => LabworkApplication(input.labwork, input.applicant, input.friends)
  }

  override implicit val mimeType: LwmMimeType = LwmMimeType.labworkApplicationV1Json

  override implicit def rdfReads: FromPG[Sesame, LabworkApplication] = defaultBindings.LabworkApplicationBinding.labworkApplicationBinder

  override implicit def classUrisFor: ClassUrisFor[Sesame, LabworkApplication] = defaultBindings.LabworkApplicationBinding.classUri

  override implicit def uriGenerator: UriGenerator[LabworkApplication] = LabworkApplication

  override implicit def rdfWrites: ToPG[Sesame, LabworkApplication] = defaultBindings.LabworkApplicationBinding.labworkApplicationBinder

  override protected def compareModel(input: LabworkApplicationProtocol, output: LabworkApplication): Boolean = {
    input.applicant == output.applicant && input.labwork == output.labwork && input.friends == output.friends
  }

  override protected def getWithFilter(queryString: Map[String, Seq[String]])(all: Set[LabworkApplication]): Try[Set[LabworkApplication]] = {
    def decode(s: String) = URLDecoder.decode(s, "UTF-8")

    queryString.foldRight(Try(all)) {
      case ((`labworkAttribute`, v), t) => t flatMap (set => Try(UUID.fromString(v.head)).map(v => set.filter(_.labwork == v)))
      case ((`applicantAttribute`, v), t) => t flatMap (set => Try(UUID.fromString(v.head)).map(v => set.filter(_.applicant == v)))
      case ((`friendAttribute`, v), t) => t flatMap (set => Try(UUID.fromString(v.head)).map(v => set.filter (_.friends.exists(_ == v))))
      case ((`dateAttribute`, v), set) => set map (_.filter (_.timestamp.toString("yyyy-MM-dd") == v.head))
      case ((`minTime`, v), t) => t flatMap (set => Try(DateTime.parse(decode(v.head))).map(t => set.filter(_.timestamp.isAfter(t))))
      case ((`maxTime`, v), t) => t flatMap (set => Try(DateTime.parse(decode(v.head))).map(t => set.filter(_.timestamp.isBefore(t))))
      case ((_, _), t) => Failure(new Throwable("Unknown attribute"))
    }
  }

  override protected def atomize(output: LabworkApplication): Try[Option[JsValue]] = {
    import defaultBindings.LabworkBinding.labworkBinder
    import defaultBindings.StudentBinding.studentBinder
    import LabworkApplication.atomicWrites

    for {
      labwork <- repository.get[Labwork](Labwork.generateUri(output.labwork)(namespace))
      applicant <- repository.get[Student](Student.generateUri(output.applicant)(namespace))
      friends <- repository.getMany[Student](output.friends.map(id => Student.generateUri(id)(namespace)))
    } yield {
      for {
        l <- labwork
        app <- applicant
      } yield {
        val atom = LabworkApplicationAtom(l, app, friends.toSet, output.timestamp, output.id)
        Json.toJson(atom)
      }
    }
  }

  override protected def atomizeMany(output: Set[LabworkApplication]): Try[JsValue] = {
    import defaultBindings.LabworkBinding.labworkBinder
    import defaultBindings.StudentBinding.studentBinder
    import LabworkApplication.atomicWrites
    import utils.Ops._
    import utils.Ops.MonadInstances.tryM

    (for {
      labworks <- repository.getMany[Labwork](output.map(lapp => Labwork.generateUri(lapp.labwork)(namespace)))
      applicants <- repository.getMany[Student](output.map(lapp => Student.generateUri(lapp.applicant)(namespace)))
      friends <- output.map(lapp => repository.getMany[Student](lapp.friends.map(id => Student.generateUri(id)(namespace)))).sequence
    } yield {
      output.foldLeft(Set.empty[LabworkApplicationAtom]) { (newSet, lapp) =>
        (for {
          l <- labworks.find(_.id == lapp.labwork)
          app <- applicants.find(_.id == lapp.applicant)
          f <- friends.find(_.map(_.id).toSet == lapp.friends)
        } yield LabworkApplicationAtom(l, app, f.toSet, lapp.timestamp, lapp.id)) match {
          case Some(atom) => newSet + atom
          case None => newSet
        }
      }
    }).map(s => Json.toJson(s))
  }
}
