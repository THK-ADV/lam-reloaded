package controllers

import java.util.UUID

import models._
import org.joda.time.Interval
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import services._
import store.SesameRepository
import store.bind.Bindings

import scala.concurrent.Future
import scala.util.control.NonFatal

class ApiDataController(private val repository: SesameRepository) extends Controller with PostgresResult {

  implicit val ns = repository.namespace
  private val bindings = Bindings[repository.Rdf](repository.namespace)

  def collisionsForCurrentLabworks() = Action { request =>
    import bindings.{SemesterDescriptor, LabworkDescriptor, ReportCardEntryDescriptor}

    val result = for {
      semester <- repository.getAll[SesameSemester]
      currentSemester = semester.find(SesameSemester.isCurrent).get
      labworks <- repository.getAll[SesameLabwork].map(_.filter(_.semester == currentSemester.id))
      cards <- repository.getAll[ReportCardEntry].map(_.filter(c => labworks.exists(_.id == c.labwork)))
      byStudents = cards.groupBy(_.student)
    } yield byStudents.mapValues(e => e.map(ee => new Interval(ee.date.toDateTime(ee.start), ee.date.toDateTime(ee.end))))

    result.get.reduce { (left, right) =>
      val overlaps = left._2.forall(i => right._2.forall(ii => i.overlaps(ii)))
      if (overlaps) println("bad")
      left
    }

    Ok
  }

  def multipleReportCardEntries(course: String) = Action { request =>
    import bindings.{LabworkDescriptor, ReportCardEntryDescriptor, AssignmentPlanDescriptor}

    for {
      labworks <- repository.getAll[SesameLabwork].map(_.filter(_.course == UUID.fromString(course)))
      _ = println(labworks)
      entries <- repository.getAll[ReportCardEntry].map(_.filter(entry => labworks.exists(_.id == entry.labwork)))
      _ = println(entries.groupBy(_.labwork).keys)
      aps <- repository.getAll[AssignmentPlan].map(_.filter(entry => labworks.exists(_.id == entry.labwork)))
      grouped = entries.groupBy(_.student)
      _ = grouped.foreach {
        case (student, reportCardEntries) if reportCardEntries.size > aps.find(_.labwork == reportCardEntries.head.labwork).get.entries.size => println(s"student $student with ${reportCardEntries.size} entries")
        case (_, reportCardEntries) if reportCardEntries.size == aps.find(_.labwork == reportCardEntries.head.labwork).get.entries.size =>
        case _ => println("oops")
      }
    } yield 1

    Ok
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  def migrateUsers = Action.async {
    import bindings.{StudentDescriptor, EmployeeDescriptor}
    import models.User.writes

    val result = for {
      _ <- UserService.createSchema
      sesameStudents <- Future.fromTry(repository.getAll[SesameStudent])
      _ = println(s"sesameStudents ${sesameStudents.size}")
      sesameEmployees <- Future.fromTry(repository.getAll[SesameEmployee]).map(_.map {
        case na if na.status == "n.a" => SesameEmployee(na.systemId, na.lastname, na.firstname, na.email, User.EmployeeType, None, na.id)
        case employee => employee
      })
      _ = println(s"sesameEmployees ${sesameEmployees.size}")
      postgresStudents = sesameStudents.map(s => DbUser(s.systemId, s.lastname, s.firstname, s.email, User.StudentType, Some(s.registrationId), Some(s.enrollment), None, s.id))
      postgresEmployees = sesameEmployees.map(e => DbUser(e.systemId, e.lastname, e.firstname, e.email, e.status, None, None, None, e.id))
      dbUsers = postgresStudents ++ postgresEmployees
      _ = println(s"dbUsers ${dbUsers.size}")
      users <- UserService.createMany(dbUsers.toList)
    } yield users.map(_.toUser)

    result.map { users =>
      println(s"users ${users.size}")
      Ok(Json.toJson(users))
    }.recover {
      case NonFatal(e) =>
        InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def migrateDegrees = Action.async {
    import bindings.DegreeDescriptor
    import models.PostgresDegree.writes

    val result = for {
      _ <- DegreeService.createSchema
      sesameDegrees <- Future.fromTry(repository.getAll[SesameDegree])
      _ = println(s"sesameDegrees ${sesameDegrees.size}")
      postgresDegrees = sesameDegrees.map(s => DegreeDb(s.label, s.abbreviation, None, s.id))
      _ = println(s"postgresDegrees ${postgresDegrees.size}")
      degrees <- DegreeService.createMany(postgresDegrees.toList)
    } yield degrees.map(_.toDegree)

    result.map { degrees =>
      Ok(Json.toJson(degrees))
    }.recover {
      case NonFatal(e) =>
        InternalServerError(Json.obj("error" -> e.getMessage))
    }
  }

  def migratePermissions = Action.async {
    import bindings.RoleDescriptor
    import models.PostgresPermission.writes

    val result = for {
      _ <- PermissionService.createSchema
      sesameRoles <- Future.fromTry(repository.getAll[SesameRole])
      permissions = Permissions.all + Permissions.prime + Permissions.god
      _ = println(s"permissions ${permissions.size}")
      sesamePermissions = sesameRoles.flatMap(_.permissions)
      _ = println(s"sesamePermissions ${sesamePermissions.filterNot(s => permissions.exists(_.value == s.value))}")
      postgresPermissions = permissions.map(p => PermissionDb(p.value, ""))
      _ = println(s"postgresPermissions ${postgresPermissions.size}")
      ps <- PermissionService.createMany(postgresPermissions.toList)
      _ = println(s"ps ${ps.size}")
    } yield ps.map(_.toPermission)

    result.jsonResult
  }

  def migrateRoles = Action.async {
    import bindings.RoleDescriptor
    import models.RolePermission.writes

    val result = for {
      _ <- RoleService2.createSchema
      _ <- RolePermissionService.createSchema
      sesameRoles <- Future.fromTry(repository.getAll[SesameRole])
      _ = println(s"sesameRoles ${sesameRoles.size}")
      postgresPermissions <- PermissionService.get()
      _ = println(s"postgresPermissions ${postgresPermissions.size}")
      postgresRoles = sesameRoles.map{ r =>
        val perms = postgresPermissions.filter(p => r.permissions.exists(_.value == p.value)).map(_.id)
        RoleDb(r.label, perms.toSet, None, r.id)
      }
      result <- RoleService2.createManyWithPermissions(postgresRoles.toList)
      foo = result.map {
        case ((o, set)) => (o, set.size)
      }
      _ = println(s"result roles")
      _ = foo.foreach(println)
    } yield result

    result.jsonResult { map =>
      Ok(Json.toJson(map.map {
        case ((r, rps)) => Json.obj(
          "role" -> Json.toJson(r.get)(Role.writes),
          "role_permission" -> Json.toJson(rps)
        )
      }))
    }
  }

  def migrateSemesters = Action.async {
    import bindings.SemesterDescriptor
    import models.LwmDateTime._

    val result = for {
      _ <- SemesterService.createSchema
      sesameSemesters <- Future.fromTry(repository.getAll[SesameSemester])
      _ = println(s"sesameSemesters ${sesameSemesters.size}")
      semesterDbs = sesameSemesters.map(s => SemesterDb(s.label, s.abbreviation, s.start.sqlDate, s.end.sqlDate, s.examStart.sqlDate, None, s.id))
      _ = println(s"semesterDbs ${semesterDbs.size}")
      semester <- SemesterService.createMany(semesterDbs.toList)
      _ = println(s"semester ${semester.size}")
    } yield semester.map(_.toSemester)

    result.jsonResult(PostgresSemester.writes)
  }

  def migrateCourses = Action.async {
    import bindings.CourseDescriptor

    val result = for {
      _ <- CourseService.createSchema
      sesameCourses <- Future.fromTry(repository.getAll[SesameCourse])
      _ = println(s"sesameCourses ${sesameCourses.size}")
      coursesDbs = sesameCourses.map(c => CourseDb(c.label, c.description, c.abbreviation, c.lecturer, c.semesterIndex, None, c.id))
      _ = println(s"coursesDbs ${coursesDbs.size}")
      courses <- CourseService.createMany(coursesDbs.toList)
      _ = println(s"courses ${courses.size}")
    } yield courses.map(_.toCourse)

    result.jsonResult
  }

  def migrateLabworks = Action.async {
    import bindings.LabworkDescriptor

    val result = for {
      _ <- LabworkService.createSchema
      sesameLabworks <- Future.fromTry(repository.getAll[SesameLabwork])
      _ = println(s"sesameLabworks ${sesameLabworks.size}")
      labworkDbs = sesameLabworks.map(l => LabworkDb(l.label, l.description, l.semester, l.course, l.degree, l.subscribable, l.published, None, l.id))
      _ = println(s"labworkDbs ${labworkDbs.size}")
      labworks <- LabworkService.createMany(labworkDbs.toList)
      _ = println(s"labworks ${labworks.size}")
    } yield labworks.map(_.toLabwork)

    result.jsonResult
  }

  def migrateLabworkApplications = Action.async {
    import bindings.LabworkApplicationDescriptor
    import models.LabworkApplicationFriend.writes

    val result = for {
      _ <- LabworkApplicationService2.createSchema
      _ <- LabworkApplicationFriendService.createSchema
      sesameLapps <- Future.fromTry(repository.getAll[SesameLabworkApplication])
      _ = println(s"sesameLapps ${sesameLapps.size}")
      lappDbs = sesameLapps.map(l => LabworkApplicationDb(l.labwork, l.applicant, l.friends, l.timestamp, None, l.id))
      _ = println(s"lappDbs ${lappDbs.size}")
      lapps <- LabworkApplicationService2.createManyWithFriends(lappDbs.toList)
      _ = lapps.foreach {
        case (app, friends) => println(s"lapps ${app.id} with friends ${friends.size}")
      }
    } yield lapps

    result.jsonResult { map =>
      Ok(Json.toJson(map.map {
        case (lapp, friends) => Json.obj(
          "labworkApplication" -> Json.toJson(lapp),
          "labworkApplicationFriends" -> Json.toJson(friends)
        )
      }))
    }
  }
}