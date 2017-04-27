package services

import models._
import org.joda.time.DateTime
import store.{PostgresDatabase, RolePermissionTable, RoleTable, TableFilter}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import models.LwmDateTime.DateTimeConverter
import slick.driver.PostgresDriver

case class RoleLabelFilter(value: String) extends TableFilter[RoleTable] {
  override def predicate = _.label.toLowerCase === value.toLowerCase
}

trait RoleService2 extends AbstractDao[RoleTable, RoleDb, Role] {
  import scala.concurrent.ExecutionContext.Implicits.global

  protected def rolePermissionService: RolePermissionService

  override val tableQuery: TableQuery[RoleTable] = TableQuery[RoleTable]

  override protected def setInvalidated(entity: RoleDb): RoleDb = {
    val now = DateTime.now.timestamp
    RoleDb(entity.label, entity.permissions, now, Some(now), entity.id)
  }

  override protected def shouldUpdate(existing: RoleDb, toUpdate: RoleDb): Boolean = {
    existing.permissions != toUpdate.permissions && existing.label == toUpdate.label
  }

  override protected def existsQuery(entity: RoleDb): Query[RoleTable, RoleDb, Seq] = {
    filterBy(List(RoleLabelFilter(entity.label)))
  }

  override protected def toAtomic(query: Query[RoleTable, RoleDb, Seq]): Future[Seq[Role]] = joinPermissions(query) {
    case (role, rolePerms) =>
      val permissions = rolePerms.map {
        case (_, perm) => PostgresPermission(perm.value, perm.description, perm.id)
      }

      PostgresRoleAtom(role.label, permissions.toSet, role.id)
  }

  override protected def toUniqueEntity(query: Query[RoleTable, RoleDb, Seq]): Future[Seq[Role]] = joinPermissions(query) {
    case (role, rolePerms) => PostgresRole(role.label, rolePerms.map(_._2.id).toSet, role.id)
  }

  private def joinPermissions(query: Query[RoleTable, RoleDb, Seq])(buildRole: (RoleDb, Seq[(RoleDb, PermissionDb)]) => Role): Future[Seq[Role]] = {
    val rolesWithPermissions = for {
      q <- query
      p <- q.permissions
    } yield (q, p)

    db.run(rolesWithPermissions.result.map(_.groupBy(_._1).map {
      case (role, rolePerms) => buildRole(role, rolePerms)
    }.toSeq))
  }

  def byUserStatus(status: String): Future[Option[RoleDb]] = {
    db.run(tableQuery.filter(_.isLabel(Roles.fromUserStatus(status))).result.headOption)
  }

  def createManyWithPermissions(roles: List[RoleDb]): Future[Map[Option[PostgresRole], Seq[RolePermission]]] = {
    for {
      rs <- createMany(roles)
      rolePermissions = roles.flatMap(r => r.permissions.map(p => RolePermission(r.id, p)))
      rps <- rolePermissionService.createMany(rolePermissions)
    } yield rps.groupBy(_.role).map {
      case ((r, rp)) => (rs.find(_.id == r).map(r => PostgresRole(r.label, r.permissions, r.id)), rp)
    }
  }
}

// TODO GET RID OF THIS, LIKE IN ASSIGNMENTPLANSERVICE
trait RolePermissionService extends AbstractDao[RolePermissionTable, RolePermission, RolePermission] { self: PostgresDatabase =>
  override val tableQuery: TableQuery[RolePermissionTable] = TableQuery[RolePermissionTable]

  override protected def setInvalidated(entity: RolePermission): RolePermission = {
    val now = DateTime.now.timestamp
    RolePermission(entity.role, entity.permission, now, Some(now), entity.id)
  }

  override protected def existsQuery(entity: RolePermission): Query[RolePermissionTable, RolePermission, Seq] = {
    tableQuery.filter(t => t.permission === entity.permission && t.role === entity.role)
  }

  override protected def shouldUpdate(existing: RolePermission, toUpdate: RolePermission): Boolean = false

  override protected def toAtomic(query: Query[RolePermissionTable, RolePermission, Seq]): Future[Seq[RolePermission]] = ???

  override protected def toUniqueEntity(query: Query[RolePermissionTable, RolePermission, Seq]): Future[Seq[RolePermission]] = ???
}

final class RoleServiceImpl(val db: PostgresDriver.backend.Database) extends RoleService2 {
  override protected def rolePermissionService: RolePermissionService = RolePermissionService
}

object RolePermissionService extends RolePermissionService with PostgresDatabase