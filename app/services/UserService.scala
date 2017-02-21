package services

import java.util.UUID

import models._
import store.{DegreeTable, UniqueTable, UserTable}

import scala.concurrent.Future
import slick.driver.PostgresDriver.api._
import slick.lifted.Rep

sealed trait TableFilter[T <: Table[_]] {
  def value: String

  def predicate: T => Rep[Boolean]
}

case class UserStatusFilter(value: String) extends TableFilter[UserTable] {
  override def predicate: (UserTable) => Rep[Boolean] = _.status.toLowerCase === value.toLowerCase
}
case class UserSystemIdFilter(value: String) extends TableFilter[UserTable] {
  override def predicate: (UserTable) => Rep[Boolean] = _.systemId.toLowerCase like s"%${value.toLowerCase}%"
}
case class UserLastnameFilter(value: String) extends TableFilter[UserTable] {
  override def predicate: (UserTable) => Rep[Boolean] = _.lastname.toLowerCase like s"%${value.toLowerCase}%"
}
case class UserFirstnameFilter(value: String) extends TableFilter[UserTable] {
  override def predicate: (UserTable) => Rep[Boolean] = _.firstname.toLowerCase like s"%${value.toLowerCase}%"
}
case class UserDegreeFilter(value: String) extends TableFilter[UserTable] {
  override def predicate: (UserTable) => Rep[Boolean] = _.enrollment.map(_ === UUID.fromString(value)).getOrElse(false)
}
case class UserIdFilter(value: String) extends TableFilter[UserTable] {
  override def predicate: (UserTable) => Rep[Boolean] = _.id === UUID.fromString(value)
}

trait UserService extends AbstractDao[UserTable, DbUser] {

  import scala.concurrent.ExecutionContext.Implicits.global

  def getUsers(userFilter: List[TableFilter[UserTable]] = List.empty, atomic: Boolean = false): Future[Seq[User]] = {
    val query = userFilter match {
      case h :: t =>
        t.foldLeft(tableQuery.filter(h.predicate)) { (query, nextFilter) =>
        query.filter(nextFilter.predicate)
      }
      case _ => tableQuery
    }

    if (atomic) {
      db.run(query.joinLeft(TableQuery[DegreeTable]).on(_.enrollment === _.id).result).map(_.map {
        case ((s, Some(d))) => PostgresStudentAtom(s.systemId, s.lastname, s.firstname, s.email, s.registrationId.head, d, s.id)
        case ((u, None)) => u.user
      })
    } else {
      db.run(query.result).map(_.map(_.user))
    }
  }

  override protected def tableQuery: TableQuery[UserTable] = TableQuery[UserTable]
}

object UserService extends UserService