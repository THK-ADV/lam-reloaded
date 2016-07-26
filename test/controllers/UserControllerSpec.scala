package controllers

import java.util.UUID

import base.TestBaseDefinition
import models.Degree
import models.users.{Employee, Student, StudentAtom, User}
import org.mockito.Matchers._
import org.openrdf.model.Value
import org.scalatest.WordSpec
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar.mock
import org.w3.banana.sesame.SesameModule
import play.api.{Application, ApplicationLoader}
import play.api.ApplicationLoader.Context
import play.api.test.{FakeRequest, WithApplicationLoader}
import play.api.http.HttpVerbs
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import services.{RoleService, SessionHandlingService}
import store.bind.Bindings
import store.sparql.{QueryEngine, QueryExecutor, SelectClause}
import store.{Namespace, SesameRepository}
import Student._
import utils.DefaultLwmApplication
import UserController.writes
import scala.util.Success
import base.StreamHandler._

class UserControllerSpec extends WordSpec with TestBaseDefinition with SesameModule {

  val repository = mock[SesameRepository]
  val roleService = mock[RoleService]
  val sessionService = mock[SessionHandlingService]
  val qe = mock[QueryExecutor[SelectClause]]
  val query = QueryEngine.empty(qe)

  val namespace = Namespace("http://lwm.gm.th-koeln.de")
  val bindings = Bindings[repository.Rdf](namespace)

  val controller: UserController = new UserController(roleService, sessionService, repository, namespace) {

    override protected def contextFrom: PartialFunction[Rule, SecureContext] = {
      case _ => NonSecureBlock
    }
  }

  class FakeApp extends WithApplicationLoader(new ApplicationLoader {
    override def load(context: Context): Application = new DefaultLwmApplication(context) {
      override def userController: UserController = controller
    }.application
  })

  "A UserController" should {

    "get one specific user, regardless of his subcategory" in {
      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", UUID.randomUUID())

      when(repository.get[User](anyObject())(anyObject())).thenReturn(Success(Some(student1)))

      val request = FakeRequest(
        HttpVerbs.GET,
        "/users/" + student1.id
      )

      val result = controller.user(student1.id.toString)(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(student1)
    }

    "get all users, regardless of their subcategory" in {
      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", UUID.randomUUID())
      val student2 = Student("mi1818", "Sanh", "Tsruw", "alb@mail.de", "44332211", UUID.randomUUID())
      val student3 = Student("wi1818", "Nahs", "Rustw", "lab@mail.de", "22331144", UUID.randomUUID())
      val employee1 = Employee("mlark", "Lars", "Marklar", "mark@mail.de", "status")
      val employee2 = Employee("mlark", "Sarl", "Ralkram", "kram@mail.de", "status")
      val employee3 = Employee("rlak", "Rasl", "Kramral", "ramk@mail.de", "status")
      val users: Set[User] = Set(student1, student2, student3, employee1, employee2, employee3)

      when(repository.getAll[User](anyObject())).thenReturn(Success(users))

      val request = FakeRequest(
        HttpVerbs.GET,
        "/users"
      )

      val result = controller.allUsers()(request)
      val expected = users map (u => Json.toJson(u))

      status(result) shouldBe OK
      contentFromStream(result) shouldBe expected
    }

    "atomize one specific user, regardless of his subcategory" in {
      val degree = Degree("Degree", "DD")
      val student = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", degree.id)
      val studentAtom = StudentAtom(student.systemId, student.lastname, student.firstname, student.email, student.registrationId, degree, student.invalidated, student.id)

      doReturn(Success(Some(student)))
        .doReturn(Success(Some(studentAtom)))
        .when(repository).get(anyObject())(anyObject())

      val request = FakeRequest(
        HttpVerbs.GET,
        "/atomic/users/" + student.id
      )

      val result = controller.userAtomic(User.generateUri(student)(namespace))(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(studentAtom)
    }

    "atomize all users, regardless of their subcategory" in {
      val degree = Degree("Degree1", "DD1")

      def atomize(s: Set[User]): Set[JsValue] = s map {
        case s: Student =>
          Json.toJson(StudentAtom(s.systemId, s.lastname, s.firstname, s.email, s.registrationId, degree, s.invalidated, s.id))
        case e: Employee =>
          Json.toJson(e)
      }
      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", degree.id)
      val studentAtom1 = StudentAtom(student1.systemId, student1.lastname, student1.firstname, student1.email, student1.registrationId, degree, student1.invalidated, student1.id)
      val student2 = Student("ai2182", "Sanh", "Tsruw", "alb@mail.de", "44332211", degree.id)
      val studentAtom2 = StudentAtom(student2.systemId, student2.lastname, student2.firstname, student2.email, student2.registrationId, degree, student1.invalidated, student2.id)
      val student3 = Student("ai3512", "Nahs", "Rustw", "lab@mail.de", "22331144", degree.id)
      val studentAtom3 = StudentAtom(student3.systemId, student3.lastname, student3.firstname, student3.email, student3.registrationId, degree, student1.invalidated, student3.id)
      val employee1 = Employee("mlark", "Lars", "Marklar", "mark@mail.de", "status")
      val employee2 = Employee("mlark", "Sarl", "Ralkram", "kram@mail.de", "status")
      val employee3 = Employee("rlak", "Rasl", "Kramral", "ramk@mail.de", "status")
      val users: Set[User] = Set(student1, student2, student3, employee1, employee2, employee3)

      when(repository.getAll[User](anyObject())).thenReturn(Success(users))
      doReturn(Success(Some(studentAtom1))).
        doReturn(Success(Some(studentAtom2))).
        doReturn(Success(Some(studentAtom3))).
        when(repository).get(anyObject())(anyObject())

      val request = FakeRequest(
        HttpVerbs.GET,
        "/atomic/users"
      )

      val result = controller.allUserAtomic()(request)
      val expected = atomize(users)

      status(result) shouldBe OK
      contentFromStream(result) shouldBe expected
    }

    "get a single student" in {
      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", UUID.randomUUID())

      when(repository.get[Student](anyObject())(anyObject())).thenReturn(Success(Some(student1)))

      val request = FakeRequest(
        HttpVerbs.GET,
        "/students/" + student1.id
      )

      val result = controller.student(student1.id.toString)(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(student1)

    }

    "atomize a single student" in {
      val degree = Degree("label", "abbrev")
      val studentAtom = StudentAtom("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", degree, None, User.randomUUID)

      when(repository.get[StudentAtom](anyObject())(anyObject())).thenReturn(Success(Some(studentAtom)))

      val request = FakeRequest(
        HttpVerbs.GET,
        "/atomic/students/" + studentAtom.id
      )

      val result = controller.studentAtomic(User.generateUri(studentAtom.id)(namespace))(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(studentAtom)
    }

    "get all students" in {
      val degree = Degree("label", "abbrev")
      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", degree.id)
      val student2 = Student("mi1818", "Sanh", "Tsruw", "alb@mail.de", "44332211", degree.id)
      val student3 = Student("wi1818", "Nahs", "Rustw", "lab@mail.de", "22331144", degree.id)

      val students: Set[Student] = Set(student1, student2, student3)

      when(repository.getAll[Student](anyObject())).thenReturn(Success(students))

      val request = FakeRequest(
        HttpVerbs.GET,
        "/students"
      )

      val result = controller.allStudents()(request)
      val expected = students map (a => Json.toJson(a))

      status(result) shouldBe OK
      contentFromStream(result) shouldBe expected
    }

    "atomize all students" in {
      val degree = Degree("Degree1", "DD1")

      val studentAtom1 = StudentAtom("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", degree, None, User.randomUUID)
      val studentAtom2 = StudentAtom("ai2182", "Sanh", "Tsruw", "alb@mail.de", "44332211", degree, None, User.randomUUID)
      val studentAtom3 = StudentAtom("ai3512", "Nahs", "Rustw", "lab@mail.de", "22331144", degree, None, User.randomUUID)

      val studentAtoms: Set[StudentAtom] = Set(studentAtom1, studentAtom2, studentAtom3)

      when(repository.getAll[StudentAtom](anyObject())).thenReturn(Success(studentAtoms))

      val request = FakeRequest(
        GET,
        "/atomic/students"
      )

      val result = controller.allAtomicStudents()(request)
      val expected = Set(studentAtom1, studentAtom2, studentAtom3) map (a => Json.toJson(a))

      status(result) shouldBe OK
      contentFromStream(result) shouldBe expected
    }

    "get a single employee" in {
      val employee1 = Employee("mlark", "Lars", "Marklar", "mark@mail.de", "status")

      when(repository.get[Employee](anyObject())(anyObject())).thenReturn(Success(Some(employee1)))

      val request = FakeRequest(
        HttpVerbs.GET,
        "/employees/" + employee1.id
      )

      val result = controller.employee(employee1.id.toString)(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(employee1)
    }

    "get all employees" in {
      val employee1 = Employee("mlark", "Lars", "Marklar", "mark@mail.de", "status")
      val employee2 = Employee("mlark", "Sarl", "Ralkram", "kram@mail.de", "status")
      val employee3 = Employee("rlak", "Rasl", "Kramral", "ramk@mail.de", "status")
      val employees: Set[Employee] = Set(employee1, employee2, employee3)

      when(repository.getAll[Employee](anyObject())).thenReturn(Success(employees))

      val request = FakeRequest(
        HttpVerbs.GET,
        "/employees"
      )

      val result = controller.allUsers()(request)
      val expected = employees map (a => Json.toJson(a))

      status(result) shouldBe OK
      contentFromStream(result) shouldBe expected
    }

    "get students specific to particular degree" in {
      val degree1 = Degree("Degree1", "DD1")

      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", degree1.id)
      val student2 = Student("ai2182", "Sanh", "Tsruw", "alb@mail.de", "44332211", degree1.id)
      val student3 = Student("mi3512", "Nahs", "Rustw", "lab@mail.de", "22331144", Degree.randomUUID)
      val students: Set[Student] = Set(student1, student2, student3)

      when(repository.getAll[Student](anyObject())).thenReturn(Success(students))

      val request = FakeRequest(
        GET,
        "/students?degree=" + degree1.id
      )

      val result = controller.allUsers()(request)
      val expected = Set(Json.toJson(student1), Json.toJson(student2))

      status(result) shouldBe OK
      contentFromStream(result) shouldBe expected
    }


    "get users specific to some particular filter attribute" in {
      import UserController.{firstnameAttribute, lastnameAttribute, statusAttribute, degreeAttribute, systemIdAttribute}

      val degree = UUID.randomUUID()
      val student1 = Student("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", degree)
      val student2 = Student("ai2182", "Sanh", "Tsruw", "alb@mail.de", "44332211", UUID.randomUUID)
      val student3 = Student("mi3512", "Nahs", "Rustw", "lab@mail.de", "22331144", degree)
      val student4 = Student("mi5323", "Nahst", "Ruste", "lab@mail.de", "3213213", UUID.randomUUID)
      val student5 = Student("mi2453", "Nahss", "Kramral", "lab@mail.de", "2312", UUID.randomUUID)
      val student6 = Student("mi3452", "Nahsa", "Rusth", "lab@mail.de", "3213123", UUID.randomUUID)
      val employee1 = Employee("mlark", "Lars", "Marklar", "mark@mail.de", "lecturer")
      val employee2 = Employee("mlark", "Sarl", "Ralkram", "kram@mail.de", "employee")
      val employee3 = Employee("rlak", "Rasl", "Rustb", "ramk@mail.de", "lecturer")
      val users: Set[User] = Set(student1, student2, student3, student4, student5, student6, employee1, employee2, employee3)
      val lecturers: Set[Employee] = Set(employee1, employee3)
      val degreers: Set[Student] = Set(student1, student3)
      val firstnamers: Set[User] = Set(student3, student4, employee3, student6)
      val lastnamer: Set[Employee] = Set(employee2)
      val systemIders: Set[Student] = Set(student1, student2)
      val systemIder: Set[Student] = Set(student5)

      when(repository.getAll[User](anyObject())).thenReturn(Success(users))

      val request1 = FakeRequest(
        HttpVerbs.GET,
        s"/users?$statusAttribute=lecturer"
      )

      val request2 = FakeRequest(
        HttpVerbs.GET,
        s"/users?$degreeAttribute=" + degree
      )

      val request3 = FakeRequest(
        HttpVerbs.GET,
        s"/users?$firstnameAttribute=" + "rust"
      )

      val request4 = FakeRequest(
        HttpVerbs.GET,
        s"/users?$lastnameAttribute=" + "Sarl"
      )

      val request5 = FakeRequest(
        HttpVerbs.GET,
        s"/users?$systemIdAttribute=" + "ai"
      )

      val request6 = FakeRequest(
        HttpVerbs.GET,
        s"/users?$systemIdAttribute=" + "mi2453"
      )

      val result1 = controller.allUsers()(request1)
      val result2 = controller.allUsers()(request2)
      val result3 = controller.allUsers()(request3)
      val result4 = controller.allUsers()(request4)
      val result5 = controller.allUsers()(request5)
      val result6 = controller.allUsers()(request6)

      val expected1 = lecturers map (l => Json.toJson(l))
      val expected2 = degreers map (s => Json.toJson(s))
      val expected3 = firstnamers map (u => Json.toJson(u))
      val expected4 = lastnamer map (e => Json.toJson(e))
      val expected5 = systemIders map (s => Json.toJson(s))
      val expected6 = systemIder map (s => Json.toJson(s))

      status(result1) shouldBe OK
      contentFromStream(result1) shouldBe expected1

      status(result2) shouldBe OK
      contentFromStream(result2) shouldBe expected2

      status(result3) shouldBe OK
      contentFromStream(result3) shouldBe expected3

      status(result4) shouldBe OK
      contentFromStream(result4) shouldBe expected4

      status(result5) shouldBe OK
      contentFromStream(result5) shouldBe expected5

      status(result6) shouldBe OK
      contentFromStream(result6) shouldBe expected6
    }

    "successfully return requested buddy by his system id" in new FakeApp {
      val degree = UUID.randomUUID
      val currentUser = Student("systemId current", "last name current", "first name current", "email current", "regId current", degree)
      val buddy = Student("systemIdBuddy", "last name buddy", "first name buddy", "email buddy", "regId buddy", degree)

      when(repository.getMany[Student](anyObject())(anyObject())).thenReturn(Success(Set(currentUser, buddy)))
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(Map.empty[String, List[Value]]))

      val request = FakeRequest(
        GET,
        s"/students/buddies/${buddy.systemId}"
      ).withSession(SessionController.userId -> currentUser.id.toString)

      val result = route(request).get

      status(result) shouldBe OK
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "OK",
        "id" -> buddy.id
      )
    }

    "not return requested buddy by his system id when degree doesn't match" in new FakeApp {
      val degree = UUID.randomUUID
      val currentUser = Student("systemId current", "last name current", "first name current", "email current", "regId current", degree)
      val buddy = Student("systemIdBuddy", "last name buddy", "first name buddy", "email buddy", "regId buddy", UUID.randomUUID)

      when(repository.getMany[Student](anyObject())(anyObject())).thenReturn(Success(Set(currentUser, buddy)))
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(Map.empty[String, List[Value]]))

      val request = FakeRequest(
        GET,
        s"/students/buddies/${buddy.systemId}"
      ).withSession(SessionController.userId -> currentUser.id.toString)

      val result = route(request).get

      status(result) shouldBe BAD_REQUEST
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "Students are not part of the same degree"
      )
    }

    "not return requested buddy when he is not found by his system id" in new FakeApp {
      val degree = UUID.randomUUID
      val currentUser = Student("systemId current", "last name current", "first name current", "email current", "regId current", degree)
      val buddy = Student("systemIdBuddy", "last name buddy", "first name buddy", "email buddy", "regId buddy", degree)

      when(repository.getMany[Student](anyObject())(anyObject())).thenReturn(Success(Set.empty[Student]))
      when(repository.prepareQuery(anyObject())).thenReturn(query)
      when(qe.parse(anyObject())).thenReturn(sparqlOps.parseSelect("SELECT * where {}"))
      when(qe.execute(anyObject())).thenReturn(Success(Map.empty[String, List[Value]]))

      val request = FakeRequest(
        GET,
        s"/students/buddies/${buddy.systemId}"
      ).withSession(SessionController.userId -> currentUser.id.toString)

      val result = route(request).get

      status(result) shouldBe NOT_FOUND
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj(
        "status" -> "KO",
        "message" -> "No such element..."
      )
    }
  }
}