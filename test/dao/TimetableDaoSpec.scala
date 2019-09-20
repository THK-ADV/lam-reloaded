package dao

import java.util.UUID

import dao.AbstractDaoSpec._
import database._
import models._
import org.joda.time.{LocalDate, LocalTime}
import play.api.inject.guice.GuiceableModule

final class TimetableDaoSpec extends AbstractExpandableDaoSpec[TimetableTable, TimetableDb, TimetableLike] {

  import slick.jdbc.PostgresProfile.api._
  import utils.date.DateTimeOps._

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val privateLabs = populateLabworks(10)(semesters, courses, degrees)
  private lazy val privateBlacklists = populateBlacklists(50)
  private lazy val privateEmployees = populateEmployees(30)

  def timetableEntryAtom(timetable: TimetableDb)(users: List[UserDb], labworks: List[LabworkDb], blacklists: List[BlacklistDb], rooms: List[RoomDb]) = {
    val entryAtoms = timetable.entries.map { e =>
      val supervisors = users.filter(u => e.supervisor.contains(u.id)).map(_.toUniqueEntity).toSet
      val room = rooms.find(_.id == e.room).get.toUniqueEntity

      TimetableEntryAtom(supervisors, room, e.dayIndex, e.start, e.end)
    }

    TimetableAtom(
      labworks.find(_.id == timetable.labwork).get.toUniqueEntity,
      entryAtoms,
      timetable.start.localDate,
      blacklists.filter(b => timetable.localBlacklist.contains(b.id)).map(_.toUniqueEntity).toSet,
      timetable.id
    )
  }

  override protected def name: String = "timetable"

  override protected val dbEntity: TimetableDb =
    TimetableDb(labworks.head.id, Set.empty, LocalDate.now.sqlDate, Set.empty)

  override protected val invalidDuplicateOfDbEntity: TimetableDb =
    dbEntity.copy(id = UUID.randomUUID)

  override protected val invalidUpdateOfDbEntity: TimetableDb =
    dbEntity.copy(labwork = UUID.randomUUID)

  override protected val validUpdateOnDbEntity: TimetableDb =
    dbEntity.copy(start = dbEntity.start.localDate.plusDays(1).sqlDate, entries = Set.empty)

  override protected val dbEntities: List[TimetableDb] = timetables

  override protected val lwmAtom: TimetableLike = timetableEntryAtom(dbEntity)(employees, labworks, blacklists, rooms)

  override protected val dependencies: DBIOAction[Unit, NoStream, Effect.Write] = DBIO.seq(
    TableQuery[UserTable].forceInsertAll(employees ++ privateEmployees),
    TableQuery[RoomTable].forceInsertAll(rooms),
    TableQuery[BlacklistTable].forceInsertAll(blacklists ++ privateBlacklists),
    TableQuery[SemesterTable].forceInsertAll(semesters),
    TableQuery[CourseTable].forceInsertAll(courses),
    TableQuery[DegreeTable].forceInsertAll(degrees),
    TableQuery[LabworkTable].forceInsertAll(privateLabs ++ labworks)
  )

  override protected val toAdd: List[TimetableDb] = populateTimetables(10, 8)(privateEmployees, privateLabs, privateBlacklists)

  override protected val numberOfUpdates: Int = 2

  override protected val numberOfDeletions: Int = 3

  override protected def update(toUpdate: List[TimetableDb]): List[TimetableDb] = {
    val chosen1 = toUpdate.head
    val chosen2 = toUpdate.last

    val entryToUpdate = TimetableEntry(
      takeSomeOf(privateEmployees).map(_.id).toSet,
      randomRoom.id,
      10,
      LocalTime.now.plusHours(10).withMillisOfSecond(0),
      LocalTime.now.plusHours(11).withMillisOfSecond(0)
    )

    List(
      chosen1.copy(entries = chosen1.entries.drop(chosen1.entries.size / 2), localBlacklist = Set.empty),
      chosen2.copy(entries = chosen2.entries + entryToUpdate)
    )
  }

  override protected def atom(dbModel: TimetableDb): TimetableLike = timetableEntryAtom(dbModel)(privateEmployees, privateLabs, privateBlacklists, rooms)

  override protected def expanderSpecs(dbModel: TimetableDb, isDefined: Boolean): DBIOAction[Unit, NoStream, Effect.Read] = {
    val timetableEntries = dao.timetableEntryQuery.filter(_.timetable === dbModel.id)

    DBIO.seq(
      dao.timetableBlacklistQuery.filter(_.timetable === dbModel.id).flatMap(_.blacklistFk).result.map { blacklists =>
        blacklists.map(_.id).toSet should contain theSameElementsAs (if (isDefined) dbModel.localBlacklist else Set.empty)
      },
      timetableEntries.result.map { entries =>
        entries.map(_.toTimetableEntry).toSet should contain theSameElementsAs (if (isDefined) dbModel.entries.map(_.copy(Set.empty)) else Set.empty)
      },
      dao.timetableEntrySupervisorQuery.filter(_.timetableEntry.in(timetableEntries.map(_.id))).flatMap(_.userFk).result.map { supervisors =>
        supervisors.map(_.id).toSet should contain theSameElementsAs (if (isDefined) dbModel.entries.flatMap(_.supervisor) else Set.empty)
      }
    )
  }

  override protected val dao: TimetableDao = app.injector.instanceOf(classOf[TimetableDao])

  override protected def bindings: Seq[GuiceableModule] = Seq.empty
}
