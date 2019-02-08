package dao

import models.Semester
import org.joda.time.LocalDate
import slick.dbio.Effect.Write
import database.{SemesterDb, SemesterTable}
import play.api.inject.guice.GuiceableModule
import slick.jdbc.PostgresProfile.api._

final class SemesterDaoSpec extends AbstractDaoSpec[SemesterTable, SemesterDb, Semester] {
  import AbstractDaoSpec._
  import utils.LwmDateTime._

  val now = LocalDate.parse("2017-01-01")
  val tomorrow = LocalDate.parse("2017-01-02")
  val exam = LocalDate.parse("2017-01-03")

  "A SemesterServiceSpec" should {

    "return current semester" in {
      val current = dbEntities.map(_.toUniqueEntity).filter(Semester.isCurrent)

      async(dao.get(List(SemesterCurrentFilter))) { result =>
        result.size shouldBe 1
        result shouldBe current
      }
    }

/*    "filter properly" in { // TODO continue
      run(DBIO.seq(
        filterBy(List(SemesterStartFilter(randomSemester.start.stringMillis))).result.map { semester =>
          semester.size shouldBe 1
        }
      ).andThen(
        filterBy(List(SemesterEndFilter(randomSemester.end.stringMillis))).result.map { semester =>
          semester.size shouldBe 1
        }
      ).andThen(
        filterBy(List(SemesterSinceFilter(dbEntities.head.start.stringMillis))).result.map { semester =>
          semester.size shouldBe dbEntities.size
        }
      ).andThen(
        filterBy(List(SemesterSinceFilter(dbEntities(maxSemesters/2).start.stringMillis))).result.map { semester =>
          semester.size shouldBe dbEntities.size - maxSemesters/2
        }
      ).andThen(
        filterBy(List(SemesterUntilFilter(dbEntities(maxSemesters/2).end.stringMillis))).result.map { semester =>
          semester.size shouldBe dbEntities.size - maxSemesters/2 + 1
        }
      ))
    }*/
  }

  override protected def name: String = "semester"

  override protected val dbEntity: SemesterDb = SemesterDb("label", "abbrev", now.sqlDate, tomorrow.sqlDate, exam.sqlDate)

  override protected val invalidDuplicateOfDbEntity: SemesterDb = {
    SemesterDb(dbEntity.label, "other abbrev", dbEntity.start, dbEntity.end, dbEntity.examStart)
  }

  override protected val invalidUpdateOfDbEntity: SemesterDb = {
    SemesterDb(dbEntity.label, "abbrev update", dbEntity.end, dbEntity.start, dbEntity.examStart, lastModified, dbEntity.invalidated, dbEntity.id)
  }

  override protected val validUpdateOnDbEntity: SemesterDb = {
    SemesterDb(dbEntity.label, "abbrev update", dbEntity.start, dbEntity.end, dbEntity.examStart, lastModified, dbEntity.invalidated, dbEntity.id)
  }

  override protected val dbEntities: List[SemesterDb] = semesters

  override protected val dependencies: DBIOAction[Unit, NoStream, Write] = DBIO.seq()

  override protected val lwmAtom: Semester = dbEntity.toUniqueEntity

  override protected val dao: AbstractDao[SemesterTable, SemesterDb, Semester] = app.injector.instanceOf(classOf[SemesterDao])

  override protected def bindings: Seq[GuiceableModule] = Seq.empty
}