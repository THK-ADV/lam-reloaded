package services

import java.util.UUID

import models.UniqueEntity
import org.joda.time.DateTime
import slick.driver.PostgresDriver.api._
import store.{PostgresDatabase, TableFilter, UniqueTable}

import scala.concurrent.Future

// TODO maybe we can get rid of DbModel
trait AbstractDao[T <: Table[DbModel] with UniqueTable, DbModel <: UniqueEntity, LwmModel <: UniqueEntity] { self: PostgresDatabase =>

  def tableQuery: TableQuery[T]

  protected def toAtomic(query: Query[T, DbModel, Seq]): Future[Seq[LwmModel]]

  protected def toUniqueEntity(query: Query[T, DbModel, Seq]): Future[Seq[LwmModel]]

  protected def setInvalidated(entity: DbModel): DbModel

  def create(entity: DbModel): Future[DbModel] = db.run((tableQuery returning tableQuery) += entity)

  def createMany(entities: Set[DbModel]): Future[Seq[DbModel]] = db.run((tableQuery returning tableQuery) ++= entities)

  def createOrUpdate(entity: DbModel): Future[Option[DbModel]] = db.run((tableQuery returning tableQuery).insertOrUpdate(entity))

  def get(tableFilter: List[TableFilter[T]] = List.empty, atomic: Boolean = true, validOnly: Boolean = true): Future[Seq[LwmModel]] = {
    val query = tableFilter match {
      case h :: t =>
        t.foldLeft(tableQuery.filter(h.predicate)) { (query, nextFilter) =>
        query.filter(nextFilter.predicate)
      }
      case _ => tableQuery
    }
    
    val valid = if (validOnly) query.filter(_.isValid) else query

    if (atomic) toAtomic(valid) else toUniqueEntity(valid)
  }

  def delete(entity: DbModel) = update0(setInvalidated(entity))

  def update(entity: DbModel): Future[Int] = update0(entity)

  private def update0(entity: DbModel) = db.run(tableQuery.filter(_.id === entity.id).update(entity))

  def createSchema = db.run(tableQuery.schema.create)

  def dropSchema = db.run(tableQuery.schema.create)
}
