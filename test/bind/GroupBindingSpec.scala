package bind

import base.SesameDbSpec
import models.users.Student
import models.{Labwork, Group}
import org.w3.banana.PointedGraph
import org.w3.banana.sesame.Sesame
import store.Namespace
import store.bind.Bindings

import scala.util.{Failure, Success}

class GroupBindingSpec extends SesameDbSpec {
  import ops._
  implicit val ns = Namespace("http://lwm.gm.fh-koeln.de/")

  val bindings = Bindings[Sesame](ns)
  import bindings.GroupBinding._
  import bindings.uuidRefBinder
  import bindings.uuidBinder

  val group = Group("Label", Labwork.randomUUID, Set(Student.randomUUID, Student.randomUUID), Group.randomUUID)
  val groupGraph = URI(Group.generateUri(group)).a(lwm.Group)
    .--(lwm.label).->-(group.label)
    .--(lwm.labwork).->-(group.labwork)(ops, uuidRefBinder(Labwork.splitter))
    .--(lwm.members).->-(group.members)(ops, uuidRefBinder(Student.splitter))
    .--(lwm.id).->-(group.id).graph

  "A GroupBindingSpec" should {
    "return a RDF graph representation of a group" in {
      val graph = group.toPG.graph

      graph isIsomorphicWith groupGraph shouldBe true
    }
    "return a group based on a RDF graph representation" in {
      val expectedGroup = PointedGraph[Rdf](URI(Group.generateUri(group)), groupGraph).as[Group]

      expectedGroup match {
        case Success(s) =>
          s shouldEqual group
        case Failure(e) =>
          fail(s"Unable to deserialise group graph: $e")
      }
    }
    }

}
