package services

import java.sql.Timestamp
import java.util.UUID

import models.UniqueEntity
import slick.driver.PostgresDriver.api._
import store.{PostgresDatabase, TableFilter, UniqueTable}

import scala.concurrent.Future

case class ModelAlreadyExists[A](value: A) extends Throwable {
  override def getMessage = s"model already exists $value"
}

// TODO maybe we can get rid of DbModel
trait AbstractDao[T <: Table[DbModel] with UniqueTable, DbModel <: UniqueEntity, LwmModel <: UniqueEntity] { self: PostgresDatabase =>
  import scala.concurrent.ExecutionContext.Implicits.global

  def tableQuery: TableQuery[T]

  protected def toAtomic(query: Query[T, DbModel, Seq]): Future[Seq[LwmModel]]

  protected def toUniqueEntity(query: Query[T, DbModel, Seq]): Future[Seq[LwmModel]]

  protected def setInvalidated(entity: DbModel): DbModel

  protected def existsQuery(entity: DbModel): Query[T, DbModel, Seq]

  protected def shouldUpdate(existing: DbModel, toUpdate: DbModel): Boolean

  protected def expandCreationOf(entity: DbModel, origin: DbModel): Future[DbModel] = Future.successful(origin)

  protected def expandUpdateOf(entity: DbModel, origin: Option[DbModel]): Future[Option[DbModel]] = Future.successful(origin)

  protected def expandDeleteOf(entity: DbModel, origin: Option[DbModel]): Future[Option[DbModel]] = Future.successful(origin)

  protected def expandDeleteOf(origin: Option[DbModel]): Future[Option[DbModel]] = Future.successful(origin)

  protected def expandCreationOf(entities: List[DbModel], origin: Seq[DbModel]): Future[Seq[DbModel]] = Future.successful(origin)

  // TODO maybe add a function which expands creation to allow database normalization

  final def create(entity: DbModel): Future[DbModel] = {
    val query = existsQuery(entity).result.flatMap { exists =>
      if (exists.nonEmpty)
        DBIO.failed(ModelAlreadyExists(exists))
      else
        (tableQuery returning tableQuery) += entity
    }

    for {
      q <- db.run(query)
      e <- expandCreationOf(entity, q)
    } yield e
  }

  final def createMany(entities: List[DbModel]): Future[Seq[DbModel]] = {
    for {
      q <- db.run((tableQuery returning tableQuery) ++= entities)
      e <- expandCreationOf(entities, q)
    } yield e
  }

  protected final def createOrUpdate(entity: DbModel): Future[Option[DbModel]] = db.run((tableQuery returning tableQuery).insertOrUpdate(entity))

  protected final def filterBy(tableFilter: List[TableFilter[T]], validOnly: Boolean = true, sinceLastModified: Option[String] = None): Query[T, DbModel, Seq] = {
    val query = tableFilter match {
      case h :: t =>
        t.foldLeft(tableQuery.filter(h.predicate)) { (query, nextFilter) =>
          query.filter(nextFilter.predicate)
        }
      case _ => tableQuery
    }

    val lastModified = sinceLastModified.fold(query)(t => query.filter(_.lastModifiedSince(new Timestamp(t.toLong))))

    if (validOnly) lastModified.filter(_.isValid) else lastModified
  }

  final def get(tableFilter: List[TableFilter[T]] = List.empty, atomic: Boolean = true, validOnly: Boolean = true, sinceLastModified: Option[String] = None): Future[Seq[LwmModel]] = {
    val query = filterBy(tableFilter, validOnly, sinceLastModified)

    if (atomic) toAtomic(query) else toUniqueEntity(query)
  }

  final def delete(entity: DbModel): Future[Option[DbModel]] = {
    val invalidated = setInvalidated(entity)
    val query = tableQuery.filter(_.id === invalidated.id).update(invalidated).map {
      case 1 => Some(invalidated)
      case _ => None
    }

    for {
      q <- db.run(query)
      e <- expandDeleteOf(entity, q)
    } yield e
  }

  final def delete(id: UUID): Future[Option[DbModel]] = {
    val found = tableQuery.filter(_.id === id)
    val query = found.result.head.flatMap { existing =>
      val invalidated = setInvalidated(existing)

      found.update(invalidated).map {
        case 1 => Some(invalidated)
        case _ => None
      }
    }

    for {
      q <- db.run(query)
      e <- expandDeleteOf(q)
    } yield e
  }

  final def update(entity: DbModel): Future[Option[DbModel]] = {
    val found = tableQuery.filter(_.id === entity.id)
    val query = found.result.head.flatMap { existing =>
      if (shouldUpdate(existing, entity))
        found.update(entity).map {
          case 1 => Some(entity)
          case _ => None
        }
      else
        DBIO.failed(ModelAlreadyExists(existing))
    }

    for {
      q <- db.run(query)
      e <- expandUpdateOf(entity, q)
    } yield e
  }

  final def createSchema = db.run(tableQuery.schema.create)

  final def dropSchema = db.run(tableQuery.schema.create)
}
