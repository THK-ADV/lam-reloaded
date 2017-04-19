package services

import java.sql.{Date, Timestamp}
import java.util.UUID

import base.PostgresDbSpec
import models._
import org.joda.time.LocalDate
import slick.dbio.Effect.Write
import store.UniqueTable
import slick.driver.PostgresDriver.api._

object AbstractDaoSpec {
  import scala.util.Random.nextInt

  val maxDegrees = 10
  val maxLabworks = 20
  val maxSemesters = 10
  val maxCourses = 10
  val maxRooms = 10
  val maxEmployees = 10

  def randomSemester = semesters(nextInt(maxSemesters))
  def randomCourse = courses(nextInt(maxCourses))
  def randomDegree = degrees(nextInt(maxDegrees))
  def randomLabwork = labworks(nextInt(maxDegrees))
  def randomRoom = rooms(nextInt(maxRooms))
  def randomEmployee = employees(nextInt(maxEmployees))

  val semesters = {
    val template = LocalDate.now.withDayOfWeek(1).withMonthOfYear(9).minusYears(5).plusMonths(6)

    (0 until maxSemesters).foldLeft((List.empty[SemesterDb], template)) {
      case ((list, t), i) =>
        val start = new Date(t.plusDays(1).toDateTimeAtStartOfDay.getMillis)
        val end = t.plusDays(1).plusMonths(6)
        val exam = new Date(t.plusDays(1).plusMonths(5).toDateTimeAtStartOfDay.getMillis)

        val current = SemesterDb(i.toString, i.toString, start, new Date(end.toDateTimeAtStartOfDay.getMillis), exam)
        (list.:+(current), end)
    }._1
  }

  val employees = (0 until maxEmployees).map(i => DbUser(i.toString, i.toString, i.toString, i.toString, User.EmployeeType, None, None)).toList

  val courses = (0 until maxCourses).map { i =>
    CourseDb(i.toString, i.toString, i.toString, randomEmployee.id, 1)
  }.toList

  val degrees = (0 until maxDegrees).map(i => DegreeDb(i.toString, i.toString)).toList

  val labworks = (0 until maxLabworks).map { i =>
    LabworkDb(i.toString, i.toString, randomSemester.id, randomCourse.id, randomDegree.id)
  }.toList

  val rooms = (0 until maxRooms).map(i => RoomDb(i.toString, i.toString)).toList
}

abstract class AbstractDaoSpec[T <: Table[DbModel] with UniqueTable, DbModel <: UniqueEntity, LwmModel <: UniqueEntity]
  extends PostgresDbSpec with AbstractDao[T, DbModel, LwmModel] {

  protected val lastModified: Timestamp = {
    import models.LwmDateTime.DateTimeConverter
    import org.joda.time.DateTime

    DateTime.now.timestamp
  }

  protected def name: String
  protected def dbEntity: DbModel
  protected def invalidDuplicateOfDbEntity: DbModel
  protected def invalidUpdateOfDbEntity: DbModel
  protected def validUpdateOnDbEntity: DbModel
  protected def dbEntities: List[DbModel]

  protected def lwmEntity: LwmModel
  protected def lwmAtom: LwmModel

  override protected def dependencies: DBIOAction[Unit, NoStream, Write]

  s"A AbstractDaoSpec with $name " should {

    s"create a $name" in {
      await(create(dbEntity)) shouldBe dbEntity
    }

    s"get a $name" in {
      await(getById(dbEntity.id.toString, atomic = false)) shouldBe Some(lwmEntity)
      await(getById(dbEntity.id.toString)) shouldBe Some(lwmAtom)
    }

    s"not create a $name because model already exists" in {
      await(create(invalidDuplicateOfDbEntity).failed) shouldBe ModelAlreadyExists(Seq(dbEntity))
    }

    s"not update a $name because model already exists" in {
      await(update(invalidUpdateOfDbEntity).failed) shouldBe ModelAlreadyExists(dbEntity)
    }

    s"update a $name properly" in {
      await(update(validUpdateOnDbEntity)) shouldBe Some(validUpdateOnDbEntity)
    }

    s"create many $name" in {
      await(createMany(dbEntities)) shouldBe dbEntities
    }

    s"delete a $name by invalidating it" in {
      await(delete(dbEntity)) shouldBe defined
    }
  }
}
