package services

import java.util.UUID

import models.UniqueEntity
import slick.driver.PostgresDriver.api._
import store.{PostgresDatabase, TableFilter, UniqueTable}

import scala.concurrent.Future

trait AbstractDao[T <: Table[DbModel] with UniqueTable, DbModel <: UniqueEntity, LwmModel <: UniqueEntity] { self: PostgresDatabase =>

  def tableQuery: TableQuery[T]

  protected def toAtomic(query: Query[T, DbModel, Seq]): Future[Seq[LwmModel]]

  protected def toUniqueEntity(query: Query[T, DbModel, Seq]): Future[Seq[LwmModel]]

  def create(entity: DbModel): Future[DbModel] = db.run((tableQuery returning tableQuery) += entity)

  def createMany(entities: Set[DbModel]): Future[Seq[DbModel]] = db.run((tableQuery returning tableQuery) ++= entities)

  def createOrUpdate(entity: DbModel): Future[Option[DbModel]] = db.run((tableQuery returning tableQuery).insertOrUpdate(entity))

  def get(tableFilter: List[TableFilter[T]] = List.empty, atomic: Boolean = true): Future[Seq[LwmModel]] = {
    val query = tableFilter match {
      case h :: t =>
        t.foldLeft(tableQuery.filter(h.predicate)) { (query, nextFilter) =>
        query.filter(nextFilter.predicate)
      }
      case _ => tableQuery
    }

    if (atomic) toAtomic(query) else toUniqueEntity(query)
  }

  def delete(id: UUID): Future[Int] = db.run(tableQuery.filter(_.id === id).delete)

  def deleteMany(ids: Set[UUID]): Future[Int] = db.run(tableQuery.filter(_.id.inSet(ids)).delete)

  def update(entity: DbModel): Future[Int] = db.run(tableQuery.filter(_.id === entity.id).update(entity))

  def createSchema = db.run(tableQuery.schema.create)

  def dropSchema = db.run(tableQuery.schema.create)
}
