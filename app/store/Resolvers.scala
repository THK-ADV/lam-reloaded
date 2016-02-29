package store

import java.util.UUID

import models.security.{Authority, RefRole, Role, Roles}
import models.users.{Employee, Student, User}
import org.w3.banana.PointedGraph
import org.w3.banana.binder.ToPG
import org.w3.banana.sesame.{Sesame, SesameModule}
import store.Prefixes.LWMPrefix
import store.bind.Bindings
import store.bind.Bindings
import store.sparql.select
import store.sparql.select._

import scala.util.{Failure, Try}

trait Resolvers {
  type R <: org.w3.banana.RDF

  def username(systemId: String): Option[UUID]

  def missingUserData[A <: User](v: A): Try[PointedGraph[R]]
}

class LwmResolvers(val repository: SesameRepository) extends Resolvers {

  import repository._

  override type R = SesameModule#Rdf
  val prefix = LWMPrefix[Sesame]
  val bindings = Bindings(repository.namespace)

  override def username(systemId: String): Option[UUID] = {
    val result = repository.query {
      select("id") where {
          ^(v("s"), p(prefix.systemId), o(systemId)).
          ^(v("s"), p(prefix.id), v("id"))
      }
    }.flatMap(_.get("id"))

    for {
      values <- result
      first <- values.headOption
    } yield UUID.fromString(first.stringValue())
  }

  override def missingUserData[A <: User](v: A): Try[PointedGraph[Sesame]] = {
    import bindings.RefRoleBinding._
    def f[Z <: User](entity: Z)(p: RefRole => Boolean)(implicit serialiser: ToPG[Sesame, Z]): Try[PointedGraph[Sesame]] =
      for {
        refroles <- repository.get[RefRole]
        filtered = refroles.find(p)
        user <- filtered match {
          case Some(refRole) =>
            import bindings.AuthorityBinding._
            for {
              user <- repository.add[Z](entity)(serialiser)
              _ <- repository.add[Authority](Authority(v.id, Set(refRole.id)))
            } yield user
          case _ => Failure(new Throwable("No appropriate RefRole found while resolving user"))
        }
      } yield user

    v match {
      case s: Student => f(s)(_.role == Roles.student.id)(bindings.StudentBinding.studentBinder)
      case e: Employee => f(e)(_.role == Roles.user.id)(bindings.EmployeeBinding.employeeBinder)
    }
  }

}
