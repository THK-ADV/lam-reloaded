package store

import java.util.UUID

import base.SesameDbSpec
import models._
import org.joda.time.{LocalDate, LocalTime}

import scala.util.{Failure, Success}

class SesameRepositorySpec extends SesameDbSpec {

  import bindings.{StudentDescriptor, uuidBinder, uuidRefBinder}
  import ops._

  "Sesame Repository" should {

    "add an entity" in {
      val student = SesameStudent("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)

      val g = repo.add(student)

      val expectedGraph = URI(User.generateUri(student)).a(lwm.User)
        .--(lwm.systemId).->-(student.systemId)
        .--(lwm.firstname).->-(student.firstname)
        .--(lwm.lastname).->-(student.lastname)
        .--(lwm.registrationId).->-(student.registrationId)
        .--(lwm.email).->-(student.email)
        .--(lwm.enrollment).->-(student.enrollment)(ops, uuidRefBinder(SesameDegree.splitter))
        .--(lwm.id).->-(student.id).graph

      g match {
        case Success(pointedGraph) =>
          pointedGraph.graph.isIsomorphicWith(expectedGraph) shouldBe true
        case Failure(e) =>
          fail(s"Addition not successful: $e")
      }
    }

    "simultaneously add many entities" in {
      val student1 = SesameStudent("mi1111", "Carl", "A", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student2 = SesameStudent("mi1112", "Claus", "B", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student3 = SesameStudent("mi1113", "Tom", "C", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student4 = SesameStudent("mi1114", "Bob", "D", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)

      val students = List(student1, student2, student3, student4)

      val g = repo.addMany(students)
      val studentsFromRepo = repo.getAll[SesameStudent]

      (g, studentsFromRepo) match {
        case (Success(graphs), Success(fromRepo)) =>
          fromRepo.size shouldBe students.size
          fromRepo foreach { student =>
            students.contains(student) shouldBe true
          }
        case _ => fail("Addition not successful")
      }
    }

    "add polymorphic entities" in {
      import bindings.UserDescriptor

      val student1 = SesameStudent("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", UUID.randomUUID())
      val student2 = SesameStudent("mi1818", "Sanh", "Tsruw", "alb@mail.de", "44332211", UUID.randomUUID())
      val student3 = SesameStudent("wi1818", "Nahs", "Rustw", "lab@mail.de", "22331144", UUID.randomUUID())

      val employee1 = SesameEmployee("mlark", "Lars", "Marklar", "mark@mail.de", "status")
      val employee2 = SesameEmployee("mlark", "Sarl", "Ralkram", "kram@mail.de", "status")
      val employee3 = SesameEmployee("rlak", "Rasl", "Kramral", "ramk@mail.de", "status")

      val users: Vector[User] = Vector(student1, student2, student3, employee1, employee2, employee3)

      repo.addMany[User](users)

      repo.getAll[User] match {
        case Success(s) => users forall s.contains shouldBe true
        case Failure(e) => fail(s"Retrieval not successful: $e")
      }
    }

    "delete an entity" in {
      val student = SesameStudent("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)

      repo add student
      repo delete User.generateUri(student)

      val explicitStudent = repo.get[SesameStudent](User.generateUri(student))

      explicitStudent match {
        case Success(Some(s)) =>
          fail("repo should've deleted the student")
        case Success(None) =>
          repo.size.get shouldBe 0
        case Failure(e) =>
          fail("repo could not return explicit entity")
      }
    }

    "get list of entities" in {
      val student1 = SesameStudent("mi1111", "Carl", "A", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student2 = SesameStudent("mi1112", "Claus", "B", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student3 = SesameStudent("mi1113", "Tom", "C", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student4 = SesameStudent("mi1114", "Bob", "D", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)

      repo.add(student1)
      repo.add(student2)
      repo.add(student3)
      repo.add(student4)

      repo.getAll[SesameStudent] match {
        case Success(students) =>
          students should contain theSameElementsAs Set(student1, student2, student3, student4)
        case Failure(e) =>
          fail(s"Could not get list of students: $e")
      }
    }

    "get an entity" in {
      val student = SesameStudent("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      repo add student

      val explicitStudent = repo.get[SesameStudent](User.generateUri(student))

      explicitStudent match {
        case Success(Some(s)) =>
          s shouldEqual student
        case Success(None) =>
          fail("repo could not unwrap an optional type")
        case Failure(e) =>
          fail("repo could not return explicit entity")
      }
    }

    "delete an arbitrarily nested entity" in {
      import bindings.{DegreeDescriptor, StudentAtomDescriptor}

      val degree = SesameDegree("label", "abbr")
      val student = SesameStudent("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", degree.id)

      repo add student
      repo add degree

      repo.delete[SesameStudentAtom](User.generateUri(student))

      val postDegree = repo get[SesameDegree] SesameDegree.generateUri(degree)
      val postStudent = repo get[SesameStudent] User.generateUri(student)

      (postDegree, postStudent) match {
        case (Success(None), Success(None)) => repo.size.get shouldBe 0
        case (Success(Some(_)), _) => fail("one of the entities was not deleted")
        case (_, Success(Some(_))) => fail("one of the entities was not deleted")
        case _ => fail(s"entities could not be deleted")
      }
    }

    "delete arbitrarily nested entities from many others" in {
      import bindings.ReportCardEntryDescriptor

      def entries(labwork: UUID, amount: Int): Vector[SesameReportCardEntry] = (0 to amount).map { i =>
        SesameReportCardEntry(UUID.randomUUID(), labwork, s"entry$i", LocalDate.now, LocalTime.now, LocalTime.now, UUID.randomUUID(),
          Set(SesameReportCardEntryType(s"type$i", i % 2 == 0, scala.util.Random.nextInt),
            SesameReportCardEntryType(s"type$i", i % 3 == 0, scala.util.Random.nextInt)))
      }.toVector

      val batch1 = entries(UUID.randomUUID(), 15)
      val batch2 = entries(UUID.randomUUID(), 15)

      repo addMany batch1
      repo addMany batch2

      repo.delete[SesameReportCardEntry](SesameReportCardEntry.generateUri(batch2.head))

      repo.getAll[SesameReportCardEntry] match {
        case Success(ents) =>
          (batch1 ++ batch2.tail) foreach { elm =>
            ents contains elm shouldBe true
          }
          ents contains batch2.head shouldBe false
        case Failure(e) => fail(s"could not get entries: ${e.getMessage}")
      }
    }

    "get a polymorphic entity" in {
      import bindings.{StudentDescriptor, UserDescriptor}

      val student1 = SesameStudent("ai1818", "Hans", "Wurst", "bla@mail.de", "11223344", UUID.randomUUID())
      val student2 = SesameStudent("mi1818", "Sanh", "Tsruw", "alb@mail.de", "44332211", UUID.randomUUID())
      val student3 = SesameStudent("wi1818", "Nahs", "Rustw", "lab@mail.de", "22331144", UUID.randomUUID())

      val employee1 = SesameEmployee("mlark", "Lars", "Marklar", "mark@mail.de", "status")
      val employee2 = SesameEmployee("mlark", "Sarl", "Ralkram", "kram@mail.de", "status")
      val employee3 = SesameEmployee("rlak", "Rasl", "Kramral", "ramk@mail.de", "status")

      val users: Vector[User] = Vector(student1, student2, student3, employee1, employee2, employee3)

      repo.addMany[User](users)

      repo.get[SesameStudent](User.generateUri(student1.id)) match {
        case Success(Some(student)) =>
        case _ => fail(s"Retrieval not successful")
      }
    }

    "simultaneously get many entities" in {
      val student1 = SesameStudent("mi1111", "Carl", "A", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student2 = SesameStudent("mi1112", "Claus", "B", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student3 = SesameStudent("mi1113", "Tom", "C", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val student4 = SesameStudent("mi1114", "Bob", "D", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)

      val students = List(student1, student2, student3, student4)

      repo.addMany(students)
      val g = repo.getMany[SesameStudent](students.map(User.generateUri))

      g match {
        case Success(s) =>
          s.toList shouldEqual students
        case Failure(e) =>
          fail(s"repo could not return many students: $e")
      }
    }

    "update an entity" in {
      val student = SesameStudent("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val studentUpdated = SesameStudent("mi1111", "Carlo", "Heinz", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)

      val g = repo.add(student)

      val expectedGraph = URI(User.generateUri(student)).a(lwm.User)
        .--(lwm.systemId).->-(student.systemId)
        .--(lwm.firstname).->-(student.firstname)
        .--(lwm.lastname).->-(student.lastname)
        .--(lwm.registrationId).->-(student.registrationId)
        .--(lwm.email).->-(student.email)
        .--(lwm.enrollment).->-(student.enrollment)(ops, uuidRefBinder(SesameDegree.splitter))
        .--(lwm.id).->-(student.id).graph

      val expectedGraphUpdated = URI(User.generateUri(studentUpdated)).a(lwm.User)
        .--(lwm.systemId).->-(studentUpdated.systemId)
        .--(lwm.firstname).->-(studentUpdated.firstname)
        .--(lwm.lastname).->-(studentUpdated.lastname)
        .--(lwm.registrationId).->-(studentUpdated.registrationId)
        .--(lwm.email).->-(studentUpdated.email)
        .--(lwm.enrollment).->-(studentUpdated.enrollment)(ops, uuidRefBinder(SesameDegree.splitter))
        .--(lwm.id).->-(studentUpdated.id).graph

      g match {
        case Success(graph) =>
          graph.graph.isIsomorphicWith(expectedGraph) shouldBe true

          implicit val generator = User
          import bindings.UserDescriptor

          val updated = repo.update[User, UriGenerator[User]](studentUpdated)
          updated match {
            case Success(pointedGraph) =>
              pointedGraph.graph.isIsomorphicWith(expectedGraphUpdated) shouldBe true
            case Failure(e) =>
              fail(s"Could not update student: $e")
          }

        case Failure(e) =>
          fail(s"Student could not be added to graph: $e")
      }
    }

    "contains an entity" in {
      val student = SesameStudent("mi1111", "Carl", "Heinz", "117272", "mi1111@gm.fh-koeln.de", UUID.randomUUID)
      val anotherStudent = SesameStudent("mi1112", "Carlo", "Heinz", "117273", "mi1112@gm.fh-koeln.de", UUID.randomUUID)

      repo add student

      val didContainStudent = repo contains User.generateUri(student)
      val didContainAnotherStudent = repo contains User.generateUri(anotherStudent)

      didContainStudent.get shouldBe true
      didContainAnotherStudent.get shouldBe false
    }

    "delete many properly" in {
      val students = (0 until 20) map (i => SesameStudent(i.toString, i.toString, i.toString, i.toString, i.toString, UUID.randomUUID))

      repo addMany students.toList

      repo deleteMany (students map User.generateUri) match {
        case Success(s) =>
          students count { student =>
            !(repo contains User.generateUri(student) getOrElse false)
          } shouldBe students.size
          repo.size.get shouldBe 0
        case Failure(e) =>
          fail(s"Deletion should succeed", e)
      }
    }

    "try to delete many, even when there is nothing to delete" in {
      val students = (0 until 20) map (i => SesameStudent(i.toString, i.toString, i.toString, i.toString, i.toString, UUID.randomUUID))

      repo deleteMany (students map User.generateUri) match {
        case Success(s) =>
          s contains Unit shouldBe true
        case Failure(e) =>
          fail(s"Deletion should succeed", e)
      }
    }

    "rollback transactions" in {
      val validStudents = (0 until 2) map (i => SesameStudent(i.toString, i.toString, i.toString, i.toString, i.toString, UUID.randomUUID))
      val invalidStudents = (0 until 3) map (i => SesameStudent(null, i.toString, i.toString, i.toString, i.toString, UUID.randomUUID))
      val students = validStudents ++ invalidStudents

      repo addMany students
      repo.getAll[SesameStudent].get shouldBe Set.empty
    }
  }

  override protected def beforeEach(): Unit = {
    repo.reset().foreach(r => assert(repo.size.get == 0))
  }

  override protected def beforeAll(): Unit = {
    repo.reset().foreach(r => assert(repo.size.get == 0))
  }
}
