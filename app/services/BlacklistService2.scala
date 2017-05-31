package services

import models.{BlacklistDb, PostgresBlacklist}
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._
import store.{BlacklistTable, TableFilter}
import models.LwmDateTime._
import org.joda.time.DateTime

import scala.concurrent.Future

case class BlacklistGlobalFilter(value: String) extends TableFilter[BlacklistTable] {
  override def predicate = _.global === value.toBoolean
}

case class BlacklistLabelFilter(value: String) extends TableFilter[BlacklistTable] {
  override def predicate = _.label.toLowerCase like s"%${value.toLowerCase}%"
}

case class BlacklistDateFilter(value: String) extends TableFilter[BlacklistTable] {
  override def predicate = _.date === value.sqlDate
}

case class BlacklistStartFilter(value: String) extends TableFilter[BlacklistTable] {
  override def predicate = _.start === value.sqlTime
}

case class BlacklistEndFilter(value: String) extends TableFilter[BlacklistTable] {
  override def predicate = _.end === value.sqlTime
}

case class BlacklistSinceFilter(value: String) extends TableFilter[BlacklistTable] {
  override def predicate = _.date >= value.sqlDate
}

case class BlacklistUntilFilter(value: String) extends TableFilter[BlacklistTable] {
  override def predicate = _.date <= value.sqlDate
}

trait BlacklistService2 extends AbstractDao[BlacklistTable, BlacklistDb, PostgresBlacklist] {
  override val tableQuery = TableQuery[BlacklistTable]

  override protected def toAtomic(query: Query[BlacklistTable, BlacklistDb, Seq]): Future[Seq[PostgresBlacklist]] = toUniqueEntity(query)

  override protected def toUniqueEntity(query: Query[BlacklistTable, BlacklistDb, Seq]): Future[Seq[PostgresBlacklist]] = {
    db.run(query.result.map(_.map(_.toBlacklist)))
  }

  override protected def setInvalidated(entity: BlacklistDb): BlacklistDb = {
    val now = DateTime.now.timestamp

    entity.copy(lastModified = now, invalidated = Some(now))
  }

  override protected def existsQuery(entity: BlacklistDb): Query[BlacklistTable, BlacklistDb, Seq] = {
    filterBy(List(
      BlacklistDateFilter(entity.date.string),
      BlacklistStartFilter(entity.start.string),
      BlacklistEndFilter(entity.end.string),
      BlacklistGlobalFilter(entity.global.toString)
    ))
  }

  override protected def shouldUpdate(existing: BlacklistDb, toUpdate: BlacklistDb): Boolean = {
    existing.label != existing.label &&
      (existing.date.equals(toUpdate.date) &&
        existing.start.equals(toUpdate.start) &&
        existing.end.equals(toUpdate.end) &&
        existing.global == toUpdate.global)
  }
}

final class BlacklistServiceImpl(val db: PostgresDriver.backend.Database) extends BlacklistService2
