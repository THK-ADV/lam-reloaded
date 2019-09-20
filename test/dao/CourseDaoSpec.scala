package dao

import java.util.UUID

import database.{CourseDb, CourseTable, UserTable}
import models._
import play.api.inject.guice.GuiceableModule
import slick.dbio.Effect.Write
import dao.helper.TableFilter

class CourseDaoSpec extends AbstractDaoSpec[CourseTable, CourseDb, CourseLike] {

  import AbstractDaoSpec._
  import slick.jdbc.PostgresProfile.api._

  override protected val dao: CourseDao = app.injector.instanceOf(classOf[CourseDao])

  "A CourseServiceSpec " should {

    "filter courses by label" in {
      val labelFilter = List(TableFilter.labelFilterEquals("3"))
      async(dao.get(labelFilter, atomic = false))(_ should contain theSameElementsAs dbEntities.filter(_.label == "3").map(_.toUniqueEntity))
    }

    "filter courses by semester index" in {
      val semesterIndexFilter = List(CourseDao.semesterIndexFilter(2))
      async(dao.get(semesterIndexFilter, atomic = false))(_ should contain theSameElementsAs dbEntities.filter(_.semesterIndex == 2).map(_.toUniqueEntity))
    }

    "filter courses by abbreviation" in {
      val abbreviationFilter = List(TableFilter.abbreviationFilter("4"))
      async(dao.get(abbreviationFilter, atomic = false))(_ should contain theSameElementsAs dbEntities.filter(_.abbreviation == "4").map(_.toUniqueEntity))
    }

    "filter courses by abbreviation and semester index" in {
      val abbreviationAndSemesterIndexFilter = List(
        TableFilter.abbreviationFilter("5"),
        CourseDao.semesterIndexFilter(2)
      )

      async(dao.get(abbreviationAndSemesterIndexFilter, atomic = false))(
        _ should contain theSameElementsAs dbEntities
          .filter(c => c.abbreviation == "5" && c.semesterIndex == 2)
          .map(_.toUniqueEntity)
      )
    }

    "filter courses by label and semester" in {
      val labelAndSemesterIndexFilter = List(
        TableFilter.labelFilterEquals("six"),
        CourseDao.semesterIndexFilter(6)
      )

      async(dao.get(labelAndSemesterIndexFilter, atomic = false))(
        _ should contain theSameElementsAs dbEntities
          .filter(course => course.label == "six" && course.semesterIndex == 6)
          .map(_.toUniqueEntity)
      )
    }
  }

  private val privateEmployees = populateEmployees(2)

  override protected val dbEntity: CourseDb =
    CourseDb("label", "description", "abbreviation", privateEmployees.head.id, 3)

  override protected val invalidDuplicateOfDbEntity: CourseDb =
    dbEntity.copy(label = "new label", id = UUID.randomUUID)

  override protected val invalidUpdateOfDbEntity: CourseDb =
    dbEntity.copy(label = "new label")

  override protected val validUpdateOnDbEntity: CourseDb =
    dbEntity.copy(label = "updated label", semesterIndex = 42, lecturer = privateEmployees.last.id)

  override protected val dbEntities: List[CourseDb] = courses

  override protected val dependencies: DBIOAction[Unit, NoStream, Write] = DBIO.seq(
    TableQuery[UserTable].forceInsertAll(employees ++ privateEmployees)
  )

  override protected val lwmAtom: CourseLike = CourseAtom(
    dbEntity.label,
    dbEntity.description,
    dbEntity.abbreviation,
    privateEmployees.head.toUniqueEntity,
    dbEntity.semesterIndex,
    dbEntity.id
  )

  override protected def name: String = "course"

  override protected def bindings: Seq[GuiceableModule] = Seq.empty
}
