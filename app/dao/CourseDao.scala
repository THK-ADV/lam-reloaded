package dao

import javax.inject.Inject
import models.{Course, CourseDb, PostgresCourseAtom}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import store.{CourseTable, TableFilter}

import scala.concurrent.Future

case class CourseLabelFilter(value: String) extends TableFilter[CourseTable] {
  override def predicate = _.label.toLowerCase like s"%${value.toLowerCase}%"
}

case class CourseSemesterIndexFilter(value: String) extends TableFilter[CourseTable] {
  override def predicate = _.semesterIndex === value.toInt
}

case class CourseAbbreviationFilter(value: String) extends TableFilter[CourseTable] {
  override def predicate = _.abbreviation.toLowerCase === value.toLowerCase
}

trait CourseDao extends AbstractDao[CourseTable, CourseDb, Course] {

  import scala.concurrent.ExecutionContext.Implicits.global

  override val tableQuery: TableQuery[CourseTable] = TableQuery[CourseTable]

  protected def authorityService: AuthorityDao

  override protected def existsQuery(entity: CourseDb): Query[CourseTable, CourseDb, Seq] = {
    filterBy(List(CourseLabelFilter(entity.label), CourseSemesterIndexFilter(entity.semesterIndex.toString)))
  }

  override protected def shouldUpdate(existing: CourseDb, toUpdate: CourseDb): Boolean = {
    (existing.description != toUpdate.description ||
      existing.abbreviation != toUpdate.abbreviation ||
      existing.lecturer != toUpdate.lecturer) &&
      (existing.semesterIndex == toUpdate.semesterIndex && existing.label == toUpdate.label)
  }

  override protected def toAtomic(query: Query[CourseTable, CourseDb, Seq]): Future[Seq[Course]] = {
    val joinedQuery = for {
      q <- query
      l <- q.joinLecturer
    } yield (q, l)

    db.run(joinedQuery.result.map(_.map {
      case (c, l) => PostgresCourseAtom(c.label, c.description, c.abbreviation, l.toLwmModel, c.semesterIndex, c.id)
    }.toSeq))
  }

  override protected def toUniqueEntity(query: Query[CourseTable, CourseDb, Seq]): Future[Seq[Course]] = {
    db.run(query.result.map(_.map(_.toLwmModel)))
  }
}

final class CourseDaoImpl @Inject()(val db: PostgresProfile.backend.Database, val authorityService: AuthorityDao) extends CourseDao

