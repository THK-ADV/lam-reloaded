package store

import java.util.UUID

import models.Degree
import models.security.{Authority, RefRole, Role, Roles}
import models.users.{Employee, Student, User}
import org.w3.banana.{PointedGraph, RDFPrefix}
import org.w3.banana.binder.ToPG
import org.w3.banana.sesame.{Sesame, SesameModule}
import store.Prefixes.LWMPrefix
import store.bind.Bindings
import store.bind.Descriptor.Descriptor
import store.sparql.select
import store.sparql.select._
import utils.Ops.MonadInstances.optM
import utils.Ops.NaturalTrasformations._

import scala.util.{Failure, Success, Try}

trait Resolvers {
  type R <: org.w3.banana.RDF

  def username(systemId: String): Try[Option[UUID]]

  def missingUserData[A <: User](v: A): Try[PointedGraph[R]]

  def degree(abbreviation: String): Try[UUID]
}

class LwmResolvers(val repository: SesameRepository) extends Resolvers {

  import repository._

  override type R = SesameModule#Rdf
  val prefix = LWMPrefix[Sesame]
  val rdf = RDFPrefix[Sesame]
  val bindings = Bindings(repository.namespace)

  override def username(systemId: String): Try[Option[UUID]] = {
    repository.prepareQuery {
      select("id") where {
        **(v("s"), p(prefix.systemId), o(systemId)).
          **(v("s"), p(prefix.id), v("id"))
      }
    }.select(_.get("id")).
      changeTo(_.headOption).
      map(value => UUID.fromString(value.stringValue())).
      run
  }

  override def missingUserData[A <: User](v: A): Try[PointedGraph[Sesame]] = {
    import bindings.
    {AuthorityDescriptor,
    RefRoleDescriptor,
    RoleDescriptor,
    StudentDescriptor,
    EmployeeDescriptor}

    def f[Z <: User](entity: Z)(p: Role => Boolean)(implicit descriptor: Descriptor[Rdf, Z]): Try[PointedGraph[Sesame]] =
      for {
        roles <- repository.getAll[Role]
        refroles <- repository.getAll[RefRole]
        refrole = for {
          role <- roles.find(p)
          refrole <- refroles.find(_.role == role.id)
        } yield refrole
        user <- refrole match {
          case Some(refRole) =>
            for {
              user <- repository.add[Z](entity)
              _ <- repository.add[Authority](Authority(v.id, Set(refRole.id)))
            } yield user
          case _ => Failure(new Throwable("No appropriate RefRole or Role found while resolving user"))
        }
      } yield user

    v match {
      case s: Student => f(s)(_.label == Roles.Student)
      case e: Employee => f(e)(_.label == Roles.Employee)
    }
  }

  override def degree(abbreviation: String): Try[UUID] = {
    import bindings.DegreeDescriptor
    import utils.Ops.MonadInstances.{tryM, optM}
    import utils.Ops.TraverseInstances.travO

    val query = select ("degree") where {
      **(v("degree"), p(rdf.`type`), s(prefix.Degree)).
      **(v("degree"), p(prefix.abbreviation), o(abbreviation))
    }

    repository.prepareQuery(query).
      select(_.get("degree")).
      changeTo(_.headOption).
      request[Option, Degree](value => repository.get[Degree](value.stringValue())).
      transform(_.fold[Try[Degree]](Failure(new Throwable(s"No viable degree found for abbreviation $abbreviation")))(Success(_))).
      map(_.id).
      run.flatten
  }
}
