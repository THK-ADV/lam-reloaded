package services

import java.sql.Timestamp
import java.util.UUID

import models._
import org.joda.time.DateTime
import models.LwmDateTime.DateTimeConverter
import slick.dbio.Effect.Write
import store._

import scala.concurrent.Future
import slick.driver.PostgresDriver.api._
import slick.lifted.Rep
import slick.profile.FixedSqlAction

case class AuthorityUserFilter(value: String) extends TableFilter[AuthorityTable] {
  override def predicate: (AuthorityTable) => Rep[Boolean] = _.user === UUID.fromString(value)
}

case class AuthorityCourseFilter(value: String) extends TableFilter[AuthorityTable] {
  override def predicate: (AuthorityTable) => Rep[Boolean] = _.course.map(_ === UUID.fromString(value)).getOrElse(false)
}

case class AuthorityRoleFilter(value: String) extends TableFilter[AuthorityTable] {
  override def predicate: (AuthorityTable) => Rep[Boolean] = _.role === UUID.fromString(value)
}

trait AuthorityService extends AbstractDao[AuthorityTable, AuthorityDb, Authority] {
  self: PostgresDatabase =>

  import scala.concurrent.ExecutionContext.Implicits.global

  protected def roleService: RoleService2

  override val tableQuery: TableQuery[AuthorityTable] = TableQuery[AuthorityTable]

  override protected def setInvalidated(entity: AuthorityDb): AuthorityDb = {
    val now = DateTime.now.timestamp

    AuthorityDb(
      entity.user,
      entity.role,
      entity.course,
      now,
      Some(now),
      entity.id
    )
  }

  override protected def shouldUpdate(existing: AuthorityDb, toUpdate: AuthorityDb): Boolean = false

  override protected def existsQuery(entity: AuthorityDb): Query[AuthorityTable, AuthorityDb, Seq] = {
    filterBy(List(AuthorityUserFilter(entity.user.toString), AuthorityRoleFilter(entity.role.toString))).filter(_.course === entity.course)
  }

  override protected def toAtomic(query: Query[AuthorityTable, AuthorityDb, Seq]): Future[Seq[Authority]] = {
    val joinedQuery = for { // TODO wait for https://github.com/slick/slick/issues/179
      ((a, c), l) <- query.joinLeft(TableQuery[CourseTable]).on(_.course === _.id).
        joinLeft(TableQuery[UserTable]).on(_._2.map(_.lecturer) === _.id)
      u <- a.userFk
      r <- a.roleFk
    } yield (a, u, r, c, l)

    db.run(joinedQuery.result.map(_.foldLeft(List.empty[PostgresAuthorityAtom]) {
      case (list, (a, u, r, Some(c), Some(l))) =>
        val courseAtom = PostgresCourseAtom(c.label, c.description, c.abbreviation, l.toUser, c.semesterIndex, c.id)
        val atom = PostgresAuthorityAtom(u.toUser, r.toRole, Some(courseAtom), a.id)

        list.+:(atom)
      case (list, (a, u, r, None, None)) =>
        list.+:(PostgresAuthorityAtom(u.toUser, r.toRole, None, a.id))
      case (list, _) => list // this should never happen
    }))
  }

  override protected def toUniqueEntity(query: Query[AuthorityTable, AuthorityDb, Seq]): Future[Seq[Authority]] = {
    db.run(query.result.map(_.map(_.toAuthority)))
  }

  def createWith(dbUser: DbUser): Future[PostgresAuthorityAtom] = {
    for {
      role <- roleService.byUserStatus(dbUser.status)
      created <- role.fold[Future[AuthorityDb]](Future.failed(new Throwable(s"No appropriate Role found while resolving user $dbUser"))) { role =>
        val authority = AuthorityDb(dbUser.id, role.id)
        create(authority)
      }
    } yield PostgresAuthorityAtom(dbUser.toUser, role.map(Role.toRole).get, None, created.id)
  }

  final def createWithCourse(course: CourseDb): DBIOAction[Seq[AuthorityDb], NoStream, Write] = {
    val result = for { // TODO replace roleService.tableQuery with roleService.get when tests are established
      rm <- DBIO.from(db.run(roleService.byRoleLabelQuery(Roles.RightsManagerLabel))) if rm.isDefined
      cm <- DBIO.from(db.run(roleService.byRoleLabelQuery(Roles.CourseManagerLabel))) if cm.isDefined

      rma = AuthorityDb(course.lecturer, rm.head.id)
      cma = AuthorityDb(course.lecturer, cm.head.id, Some(course.id))

      hasRM <- DBIO.from(db.run(filterBy(List(AuthorityUserFilter(course.lecturer.toString), AuthorityRoleFilter(rm.head.id.toString))).exists.result))
      authoritiesToCreate = {
        if (hasRM) {
          Seq(cma)
        } else {
          Seq(cma, rma)
        }
      }

      authorities <- createManyQuery(authoritiesToCreate)
    } yield authorities

    result
  }

  final def updateWithCourse(course: CourseDb): DBIOAction[Int, NoStream, Write] = {
     val result = for{
      cm <- DBIO.from(db.run(roleService.tableQuery.filter(_.label === Roles.CourseManagerLabel).map(_.id).result.headOption)) if cm.isDefined
      authoritiesWithCourse <- DBIO.from(db.run(tableQuery.filter(auth => auth.course === course.id && auth.role === cm.get).result))
      _ = println(s"authoritiesWithCourse $authoritiesWithCourse")
      rows <- {
        if (!authoritiesWithCourse.exists(_.user == course.lecturer))
          for {
            // delete auth of old cm
            deletedCM <- tableQuery.filter(_.id.inSet(authoritiesWithCourse.map(_.id))).delete

            // delete rm auth if needed
            deletedRM <- deleteSingleRightsManager(course.lecturer)

            // create new cm auth and rm if needed
            newAuths <- createWithCourse(course) if newAuths.nonEmpty
            _ = println(s"newAuths $newAuths")
          } yield newAuths.size + deletedCM + deletedRM
        else
          DBIO.successful(0)
      }
    } yield rows

    DBIO.from(db.run(result))
  }

  def deleteSingleRightsManager(lecturer: UUID): DBIOAction[Int, NoStream, Write] ={
    val a = for {
      hasCourse <- filterBy(List(AuthorityUserFilter(lecturer.toString))).filter(_.course.isDefined).exists.result
      rm <- roleService.tableQuery.filter(_.label === Roles.RightsManagerLabel).map(_.id).result.headOption if rm.isDefined
      deletedRM <- {
        if (hasCourse) {
          DBIO.successful(0)
        } else {
          filterBy(List(AuthorityUserFilter(lecturer.toString), AuthorityRoleFilter(rm.get.toString))).delete
        }
      }
    } yield deletedRM

    DBIO.from(db.run(a))
  }

  final def deleteWithCourse(course: CourseDb): DBIOAction[Int, NoStream, Write] ={
      //TODO Delete RightsManager if no courses left
      val result = for{
        deleted <- filterBy(List(AuthorityCourseFilter(course.id.toString), AuthorityUserFilter(course.lecturer.toString))).delete

        deletedAuthority <- deleteSingleRightsManager(course.lecturer)

      }yield deletedAuthority + deleted

    DBIO.from(db.run(result))
  }
}

object AuthorityService extends AuthorityService with PostgresDatabase {
  override protected def roleService: RoleService2 = RoleService2
}
