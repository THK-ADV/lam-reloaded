package dao

import java.util.UUID

import models.LwmDateTime._
import models._
import services.{ScheduleEntryGen, ScheduleGen}
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._
import store._

import scala.concurrent.Future

case class ScheduleEntryLabworkFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.labwork === UUID.fromString(value)
}

case class ScheduleEntryCourseFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.labworkFk.map(_.course).filter(_ === UUID.fromString(value)).exists
}

case class ScheduleEntryGroupFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.group === UUID.fromString(value)
}

case class ScheduleEntrySupervisorFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = e => TableQuery[ScheduleEntrySupervisorTable].filter(s => s.scheduleEntry === e.id && s.supervisor === UUID.fromString(value)).exists
}

case class ScheduleEntryDateFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.date === value.sqlDateFromMillis
}

case class ScheduleEntryStartFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.start === value.sqlTimeFromMillis
}

case class ScheduleEntryEndFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.end === value.sqlTimeFromMillis
}

case class ScheduleEntrySinceFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.date >= value.sqlDateFromMillis
}

case class ScheduleEntryUntilFilter(value: String) extends TableFilter[ScheduleEntryTable] {
  override def predicate = _.date <= value.sqlDateFromMillis
}

trait ScheduleEntryDao extends AbstractDao[ScheduleEntryTable, ScheduleEntryDb, ScheduleEntry] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override val tableQuery = TableQuery[ScheduleEntryTable]
  protected val scheduleEntrySupervisorQuery: TableQuery[ScheduleEntrySupervisorTable] = TableQuery[ScheduleEntrySupervisorTable]
  protected val groupQuery: TableQuery[GroupTable] = TableQuery[GroupTable]
  protected val groupMembershipQuery: TableQuery[GroupMembershipTable] = TableQuery[GroupMembershipTable]

  override protected def toAtomic(query: Query[ScheduleEntryTable, ScheduleEntryDb, Seq]): DBIOAction[Seq[ScheduleEntry], NoStream, Effect.Read] = collectDependencies(query) {
    case ((e, r, lab, c, d, s, lec), g, subs) =>
      val labwork = {
        val course = PostgresCourseAtom(c.label, c.description, c.abbreviation, lec.toLwmModel, c.semesterIndex, c.id)
        PostgresLabworkAtom(lab.label, lab.description, s.toLwmModel, course, d.toLwmModel, lab.subscribable, lab.published, lab.id)
      }

      PostgresScheduleEntryAtom(labwork, e.start.localTime, e.end.localTime, e.date.localDate, r.toLwmModel, subs.map(_._2.toLwmModel).toSet, g.toLwmModel, e.id)
  }

  override protected def toUniqueEntity(query: Query[ScheduleEntryTable, ScheduleEntryDb, Seq]): DBIOAction[Seq[ScheduleEntry], NoStream, Effect.Read] = collectDependencies(query) {
    case ((e, _, _, _, _, _, _), _, subs) => PostgresScheduleEntry(e.labwork, e.start.localTime, e.end.localTime, e.date.localDate, e.room, subs.map(_._1.supervisor).toSet, e.group, e.id)
  }

  override protected def existsQuery(entity: ScheduleEntryDb): Query[ScheduleEntryTable, ScheduleEntryDb, Seq] = {
    filterBy(List(
      ScheduleEntryLabworkFilter(entity.labwork.toString),
      ScheduleEntryGroupFilter(entity.group.toString)
    ))
  }

  override protected def shouldUpdate(existing: ScheduleEntryDb, toUpdate: ScheduleEntryDb): Boolean = {
    (existing.supervisor != toUpdate.supervisor ||
    !existing.date.equals(toUpdate.date) ||
    !existing.start.equals(toUpdate.start) ||
    !existing.end.equals(toUpdate.end) ||
    existing.room != toUpdate.room) &&
      (existing.labwork == toUpdate.labwork && existing.group == toUpdate.group)
  }

  private def collectDependencies(query: Query[ScheduleEntryTable, ScheduleEntryDb, Seq])
                                 (build: ((ScheduleEntryDb, RoomDb, LabworkDb, CourseDb, DegreeDb, SemesterDb, DbUser), GroupDb, Seq[(ScheduleEntrySupervisor, DbUser)]) => ScheduleEntry) = {
    val mandatory = for {
      q <- query
      r <- q.roomFk
      g <- q.groupFk
      l <- q.labworkFk
      c <- l.courseFk
      d <- l.degreeFk
      s <- l.semesterFk
      lec <- c.lecturerFk
    } yield (q, r, g, l, c, d, s, lec)

    val supervisors = for {
      s <- scheduleEntrySupervisorQuery
      u <- s.supervisorFk
    } yield (s, u)

    val group = groupQuery.joinLeft(groupMembershipQuery).on(_.id === _.group)

    mandatory.joinLeft(supervisors).on(_._1.id === _._1.scheduleEntry).joinLeft(group).on(_._1._1.group === _._1.id).result.map(_.groupBy(_._1._1._1.id).map {
      case (id, dependencies) =>
        val (((se, r, g, l, c, d, s, lec), _), _) = dependencies.find(_._1._1._1.id == id).get
        val sups = dependencies.flatMap(_._1._2)
        val gm = dependencies.flatMap(_._2.flatMap(_._2))

        build((se, r, l, c, d, s, lec), g.copy(members = gm.map(_.student).toSet), sups)
    }.toSeq)
  }

  private def collectDependenciesMin[A, B](query: Query[ScheduleEntryTable, ScheduleEntryDb, Seq])
                                          (build: (ScheduleEntryDb, GroupDb, Seq[(ScheduleEntrySupervisor, DbUser)]) => A)
                                          (transform: (Seq[A]) => Vector[B]): Future[Vector[B]] = {
    val mandatory = for {
      q <- query
      g <- q.groupFk
    } yield (q, g)

    val supervisors = for {
      s <- scheduleEntrySupervisorQuery
      u <- s.supervisorFk
    } yield (s, u)

    val group = groupQuery.joinLeft(groupMembershipQuery).on(_.id === _.group)

    val action = mandatory.joinLeft(supervisors).on(_._1.id === _._1.scheduleEntry).joinLeft(group).on(_._1._1.group === _._1.id).result.map(_.groupBy(_._1._1._1.id).map {
      case (id, dependencies) =>
        val (((se, g), _), _) = dependencies.find(_._1._1._1.id == id).get
        val sups = dependencies.flatMap(_._1._2)
        val gm = dependencies.flatMap(_._2.flatMap(_._2))

        build(se, g.copy(members = gm.map(_.student).toSet), sups)
    }.toSeq)

    db.run(action map transform)
  }

  override protected def databaseExpander: Option[DatabaseExpander[ScheduleEntryDb]] = Some(new DatabaseExpander[ScheduleEntryDb] {
    override def expandCreationOf[E <: Effect](entities: Seq[ScheduleEntryDb]) = {
      for {
        _ <- scheduleEntrySupervisorQuery ++= entities.flatMap { e =>
          e.supervisor.map(u => ScheduleEntrySupervisor(e.id, u))
        }
      } yield entities
    }

    override def expandDeleteOf(entity: ScheduleEntryDb) = {
      (for {
        d <- scheduleEntrySupervisorQuery.filter(_.scheduleEntry === entity.id).delete
      } yield d).map(_ => Some(entity))
    }

    override def expandUpdateOf(entity: ScheduleEntryDb) = {
      for {
        d <- expandDeleteOf(entity) if d.isDefined
        c <- expandCreationOf(Seq(entity))
      } yield c.headOption
    }
  })

  /*
  val comps = all
        .filter(_.labwork.course.semesterIndex == labwork.course.semesterIndex)
        .filter(_.labwork.semester.id == labwork.semester.id)
        .filter(_.labwork.degree.id == labwork.degree.id)
        .filterNot(_.labwork.id == labwork.id)
   */

  def competitive(labwork: PostgresLabworkAtom): Future[Vector[ScheduleGen]] = {
    val comps = for {
      t <- tableQuery
      l <- t.labworkFk if l.id =!= labwork.id
      c <- l.courseFk if c.semesterIndex === labwork.course.semesterIndex
      s <- l.semesterFk if s.id === labwork.semester.id
      d <- l.degreeFk if d.id === labwork.degree.id
    } yield t

    scheduleGen(comps)
  }

  def scheduleGenBy(labworkId: String) = {
    val query = for {
      t <- tableQuery if t.labwork === UUID.fromString(labworkId)
    } yield t

    scheduleGen(query)
  }

  private def scheduleGen(query: Query[ScheduleEntryTable, ScheduleEntryDb, Seq]): Future[Vector[ScheduleGen]] = {
    collectDependenciesMin(query) {
      case (se, g, sups) => (ScheduleEntryGen(
        se.start.localTime,
        se.end.localTime,
        se.date.localDate,
        se.room,
        sups.map(_._1.supervisor).toSet,
        g.toLwmModel
      ), se.labwork)
    } { entries =>
      entries.groupBy(_._2).map {
        case (id, e) => ScheduleGen(id, e.map(_._1).toVector)
      }.toVector
    }
  }


  private lazy val schemas = List(
    tableQuery.schema,
    scheduleEntrySupervisorQuery.schema
  )

  override def createSchema: Future[Unit] = {
    db.run(DBIO.seq(schemas.map(_.create): _*).transactionally)
  }

  override def dropSchema: Future[Unit] = {
    db.run(DBIO.seq(schemas.reverseMap(_.drop): _*).transactionally)
  }
}

final class ScheduleEntryDaoImpl(val db: PostgresDriver.backend.Database) extends ScheduleEntryDao
