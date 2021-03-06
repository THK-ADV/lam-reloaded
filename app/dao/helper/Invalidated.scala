package dao.helper

import database.UniqueTable
import models.UniqueDbEntity
import org.joda.time.DateTime
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.sql.FixedSqlAction
import utils.date.DateTimeOps.DateTimeConverter

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.Future

trait Invalidated[T <: Table[DbModel] with UniqueTable, DbModel <: UniqueDbEntity] {
  self: Core
    with Expandable[DbModel]
    with Accessible[T, DbModel]
    with Retrieved[T, DbModel, _] =>

  final def invalidate(entity: DbModel): Future[DbModel] = invalidate(entity.id)

  final def invalidate(id: UUID): Future[DbModel] = db.run(invalidateSingle(id))

  final def invalidateManyEntities(entities: List[DbModel]): Future[List[DbModel]] = invalidateMany(entities.map(_.id))

  final def invalidateMany(ids: List[UUID]): Future[List[DbModel]] = db.run(invalidateManyQuery(ids))

  final def invalidateManyQuery(ids: List[UUID]) = DBIO.sequence(ids.map(invalidateSingle(_)))

  final def invalidateSingle(id: UUID, now: Timestamp = DateTime.now.timestamp) = {
    invalidateSingle0(filterValidOnly(_.id === id), now)
  }

  final def invalidateSingleWhere(where: T => Rep[Boolean], now: Timestamp = DateTime.now.timestamp) = {
    invalidateSingle0(filterValidOnly(where), now)
  }

  final def invalidateSingleQuery(query: Query[T, DbModel, Seq], now: Timestamp = DateTime.now.timestamp) = {
    invalidateSingle0(query, now)
  }

  final def deleteHardQuery(id: UUID): FixedSqlAction[Int, NoStream, Effect.Write] =
    deleteManyHardQuery(List(id))

  final def deleteManyHardQuery(ids: List[UUID]): FixedSqlAction[Int, NoStream, Effect.Write] =
    tableQuery.filter(_.id.inSet(ids)).delete

  final def deleteHard(ids: List[UUID]): Future[Int] =
    db.run(deleteManyHardQuery(ids))

  private def invalidateSingle0(query: Query[T, DbModel, Seq], now: Timestamp) = {
    val singleQuery = query.exactlyOne { toDelete =>
      for {
        _ <- query.map(f => (f.lastModified, f.invalidated)).update((now, Some(now)))
      } yield toDelete
    }

    val expandableQuery = databaseExpander.fold {
      singleQuery
    } { expander =>
      for {
        q <- singleQuery
        e <- expander.expandDeleteOf(q)
      } yield e
    }

    expandableQuery.transactionally
  }
}
