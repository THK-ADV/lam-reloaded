package dao

import java.util.UUID

import models.LwmDateTime._
import models._
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._
import slick.lifted.TableQuery
import store._

import scala.concurrent.Future

case class ReportCardEntryStudentFilter(value: String) extends TableFilter[ReportCardEntryTable] {
  override def predicate = _.student === UUID.fromString(value)
}

case class ReportCardEntryLabworkFilter(value: String) extends TableFilter[ReportCardEntryTable] {
  override def predicate = _.labwork === UUID.fromString(value)
}

case class ReportCardEntryCourseFilter(value: String) extends TableFilter[ReportCardEntryTable] {
  override def predicate = _.labworkFk.map(_.course).filter(_ === UUID.fromString(value)).exists
}

case class ReportCardEntryRoomFilter(value: String) extends TableFilter[ReportCardEntryTable] {
  override def predicate = _.room === UUID.fromString(value)
}

case class ReportCardEntryScheduleEntryFilter(value: String) extends TableFilter[ReportCardEntryTable] {
  override def predicate = r => TableQuery[ScheduleEntryTable].filter { s =>
    s.id === UUID.fromString(value) &&
      ((s.labwork === r.labwork && s.room === r.room && s.start === r.start && s.end === r.end && s.date === r.date) ||
        TableQuery[ReportCardRescheduledTable].filter(rs => rs.reportCardEntry === r.id && rs.room === r.room && rs.start === r.start && rs.end === r.end && rs.date === r.date).exists ||
        TableQuery[ReportCardRetryTable].filter(rt => rt.reportCardEntry === r.id && rt.room === r.room && rt.start === r.start && rt.end === r.end && rt.date === r.date).exists)
  }.exists
}

trait ReportCardEntryDao extends AbstractDao[ReportCardEntryTable, ReportCardEntryDb, ReportCardEntry] {

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val schemas = List(
    tableQuery.schema,
    rescheduledQuery.schema,
    retryQuery.schema,
    entryTypeQuery.schema
  )
  override val tableQuery = TableQuery[ReportCardEntryTable]
  protected val entryTypeQuery: TableQuery[ReportCardEntryTypeTable] = TableQuery[ReportCardEntryTypeTable]
  protected val retryQuery: TableQuery[ReportCardRetryTable] = TableQuery[ReportCardRetryTable]
  protected val rescheduledQuery: TableQuery[ReportCardRescheduledTable] = TableQuery[ReportCardRescheduledTable]

  def reportCardsFrom(srcStudent: UUID, labwork: UUID) = collectDependencies(tableQuery.filter(r => r.labwork === labwork && r.student === srcStudent)) {
    case ((entry, _, _, _), _, _, entryTypes) => entry.copy(entryTypes = entryTypes.toSet)
  }

  def createManyExpanded(copied: Seq[ReportCardEntryDb]) = createManyQuery(copied).flatMap(_ => databaseExpander.get.expandCreationOf(copied))

  override protected def databaseExpander: Option[DatabaseExpander[ReportCardEntryDb]] = Some(new DatabaseExpander[ReportCardEntryDb] {
    override def expandCreationOf[E <: Effect](entities: Seq[ReportCardEntryDb]) = { // entry -> types, rescheduled, (retry -> types)
      val rts = entities.flatMap(_.retry)
      val rtTypes = rts.flatMap(_.entryTypes)

      for {
        _ <- rescheduledQuery ++= entities.flatMap(_.rescheduled)
        _ <- retryQuery ++= rts
        _ <- entryTypeQuery ++= entities.flatMap(_.entryTypes) ++ rtTypes
      } yield entities
    }

    override def expandDeleteOf(entity: ReportCardEntryDb) = { // entry -> types, rescheduled, (retry -> types)
      val rs = rescheduledQuery.filter(_.reportCardEntry === entity.id)
      val rt = retryQuery.filter(_.reportCardEntry === entity.id)
      val types = entryTypeQuery.filter(t => t.reportCardEntry === entity.id || t.reportCardRetry.in(rt.map(_.id)))

      val deleted = for {
        d1 <- types.delete
        d2 <- rt.delete
        d3 <- rs.delete
      } yield d1 + d2 + d3

      deleted.map(_ => Some(entity))
    }

    override def expandUpdateOf(entity: ReportCardEntryDb) = DBIO.successful(Some(entity)) // entry only
  })

  override def createSchema: Future[Unit] = {
    db.run(DBIO.seq(schemas.map(_.create): _*).transactionally)
  }

  override def dropSchema: Future[Unit] = {
    db.run(DBIO.seq(schemas.reverseMap(_.drop): _*).transactionally)
  }

  override protected def toAtomic(query: Query[ReportCardEntryTable, ReportCardEntryDb, Seq]): Future[Seq[ReportCardEntry]] = db.run(collectDependencies(query) {
    case ((entry, labwork, student, room), optRs, optRt, entryTypes) => PostgresReportCardEntryAtom(
      student.toLwmModel,
      labwork.toLwmModel,
      entry.label,
      entry.date.localDate,
      entry.start.localTime,
      entry.end.localTime,
      room.toLwmModel,
      entryTypes.map(_.toLwmModel).toSet,
      optRs.map { case (rs, r) => PostgresReportCardRescheduledAtom(rs.date.localDate, rs.start.localTime, rs.end.localTime, r.toLwmModel, rs.reason, rs.id) },
      optRt.map { case (rt, r) => PostgresReportCardRetryAtom(rt.date.localDate, rt.start.localTime, rt.end.localTime, r.toLwmModel, rt.entryTypes.map(_.toLwmModel), rt.reason, rt.id) },
      entry.id
    )
  })

  override protected def toUniqueEntity(query: Query[ReportCardEntryTable, ReportCardEntryDb, Seq]): Future[Seq[ReportCardEntry]] = db.run(collectDependencies(query) {
    case ((entry, _, _, _), optRs, optRt, entryTypes) => entry.copy(entryTypes = entryTypes.toSet, retry = optRt.map(_._1), rescheduled = optRs.map(_._1)).toLwmModel
  })

  private def collectDependencies[A](query: Query[ReportCardEntryTable, ReportCardEntryDb, Seq])
                                    (build: ((ReportCardEntryDb, LabworkDb, DbUser, RoomDb), Option[(ReportCardRescheduledDb, RoomDb)], Option[(ReportCardRetryDb, RoomDb)], Seq[ReportCardEntryTypeDb]) => A) = {
    val mandatory = for {
      q <- query
      l <- q.labworkFk
      s <- q.studentFk
      r <- q.roomFk
    } yield (q, l, s, r)

    val retries = for {
      rt <- retryQuery.joinLeft(entryTypeQuery).on(_.id === _.reportCardRetry)
      r <- rt._1.roomFk
    } yield (rt, r)

    val rescheduled = for {
      rs <- rescheduledQuery
      r <- rs.roomFk
    } yield (rs, r)

    mandatory.joinLeft(rescheduled).on(_._1.id === _._1.reportCardEntry).joinLeft(retries).on(_._1._1.id === _._1._1.reportCardEntry).joinLeft(entryTypeQuery).on(_._1._1._1.id === _.reportCardEntry).result.map(_.groupBy(_._1._1._1._1.id).map {
      case (id, dependencies) =>
        val (((entry, rescheduled), retry), _) = dependencies.find(_._1._1._1._1.id == id).get // lhs first, which should be the grouped key
      val retryEntryTypes = dependencies.flatMap(_._1._2.flatMap(_._1._2)) // resolve other n to m relationship
      val entryTypes = dependencies.flatMap(_._2) // rhs next, which should be the grouped values, the reason we grouped for
      val retryWithEntryTypes = retry.map(t => (t._1._1.copy(entryTypes = retryEntryTypes.toSet), t._2))

        build(entry, rescheduled, retryWithEntryTypes, entryTypes)
    }.toSeq)
  }

  override protected def existsQuery(entity: ReportCardEntryDb): Query[ReportCardEntryTable, ReportCardEntryDb, Seq] = {
    filterBy(List(
      ReportCardEntryLabworkFilter(entity.labwork.toString),
      ReportCardEntryStudentFilter(entity.student.toString)
    ))
  }

  override protected def shouldUpdate(existing: ReportCardEntryDb, toUpdate: ReportCardEntryDb): Boolean = {
    existing.labwork == toUpdate.labwork && existing.student == toUpdate.student
  }
}

final class ReportCardEntryDaoImpl(val db: PostgresDriver.backend.Database) extends ReportCardEntryDao