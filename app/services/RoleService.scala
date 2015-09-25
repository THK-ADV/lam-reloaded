package services

import java.util.UUID

import models.security._
import store.Prefixes.LWMPrefix
import store.SesameRepository
import store.bind.Bindings

trait RoleServiceLike {

  /**
   * Retrieves the authority of a particular user.
   * @param userId User ID
   * @return User's possible authority
   */
  def authorityFor(userId: String): Option[Authority]

  /**
   * Checks if the `checker` is allowed to pass the restrictions defined in `checkee`
   * @param checkee restrictions
   * @param checker to be checked
   * @return true/false
   */
  def checkWith(checkee: (Option[UUID], Set[Permission]))(checker: Set[RefRole]): Boolean

  /**
   * Composition between `authorityFor` and `checkWith` functions.
   * Checks if a particular user is allowed to pass the restrictions defined in `checkee`
   *
   * @param checkee restrictions
   * @param userId User ID
   * @return true/false
   */
  def checkFor(checkee: (Option[UUID], Set[Permission]))(userId: String): Boolean = authorityFor(userId) exists (e => checkWith(checkee)(e.refRoles))
}

class RoleService(repository: SesameRepository) extends RoleServiceLike {

  import repository._

  private val lwm = LWMPrefix[Rdf]
  private val bindings = Bindings[Rdf](namespace)

  override def authorityFor(userId: String): Option[Authority] = {
    import store.sparql.select
    import store.sparql.select._
    import bindings.AuthorityBinding._
    import bindings.RefRoleBinding._

    repository.query {
      select("auth") where {
        ^(v("auth"), p(lwm.privileged), o(userId))
      }
    }.flatMap(_.get("auth")).flatMap(v => get[Authority](v.stringValue()).toOption.flatten)
  }


  override def checkWith(checkee: (Option[UUID], Set[Permission]))(checker: Set[RefRole]): Boolean = checkee match {
    case (module, permissions) =>
      import bindings.RoleBinding._
      (for {
        ref <- checker.find(_.module == checkee._1)
        role <- repository.get[Role](Role.generateUri(ref.role)).toOption.flatten
      } yield permissions.forall(role.permissions.contains)) getOrElse checker.exists(_.role == Roles.admin.id)

  }
}