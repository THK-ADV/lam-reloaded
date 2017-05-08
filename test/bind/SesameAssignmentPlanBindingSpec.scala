package bind

import java.util.UUID

import base.SesameDbSpec
import models.SesameAssignmentEntryType._
import models.{SesameAssignmentEntry, SesameAssignmentPlan, SesameLabwork}
import org.w3.banana.PointedGraph

import scala.util.{Failure, Success}

class SesameAssignmentPlanBindingSpec extends SesameDbSpec {

  import bindings.{AssignmentEntryDescriptor, AssignmentEntryTypeDescriptor, AssignmentPlanDescriptor, dateTimeBinder, uuidBinder, uuidRefBinder}
  import ops._

  implicit val assignmentPlanBinder = AssignmentPlanDescriptor.binder
  implicit val assignmentEntryBinder = AssignmentEntryDescriptor.binder
  implicit val assignmentEntryTypeBinder = AssignmentEntryTypeDescriptor.binder

  val assignmentPlan = SesameAssignmentPlan(UUID.randomUUID(), 2, 2, Set(
    SesameAssignmentEntry(0, "label 1", Set(Attendance, Certificate)),
    SesameAssignmentEntry(1, "label 2", Set(Attendance, Bonus))
  ))

  val assignmentEntry = assignmentPlan.entries.head

  val assignmentPlanGraph = (
    URI(SesameAssignmentPlan.generateUri(assignmentPlan)).a(lwm.AssignmentPlan)
      .--(lwm.labwork).->-(assignmentPlan.labwork)(ops, uuidRefBinder(SesameLabwork.splitter))
      -- lwm.attendance ->- assignmentPlan.attendance
      -- lwm.mandatory ->- assignmentPlan.mandatory
      -- lwm.entries ->- assignmentPlan.entries
      -- lwm.invalidated ->- assignmentPlan.invalidated
      -- lwm.id ->- assignmentPlan.id
    ).graph

  val assignmentEntryGraph = (
    URI("#").a(lwm.AssignmentEntry)
      -- lwm.index ->- assignmentEntry.index
      -- lwm.label ->- assignmentEntry.label
      -- lwm.entryTypes ->- assignmentEntry.types
      -- lwm.duration ->- assignmentEntry.duration
    ).graph

  "An AssignmentPlanBinding" should {

    "successfully serialise an assignmentPlan" in {
      val plan = assignmentPlanBinder.fromPG(assignmentPlan.toPG)

      plan shouldBe Success(assignmentPlan)
    }

    "successfully serialise an assignmentEntry" in {
      val entry = assignmentEntryBinder.fromPG(assignmentEntry.toPG)

      entry shouldBe Success(assignmentEntry)
    }

    "return an assignmentPlan based on a RDF representation" in {
      val expectedAssignmentPlan = PointedGraph[Rdf](URI(SesameAssignmentPlan.generateUri(assignmentPlan)), assignmentPlanGraph).as[SesameAssignmentPlan]

      expectedAssignmentPlan match {
        case Success(s) =>
          s shouldEqual assignmentPlan
        case Failure(e) =>
          fail(s"Unable to deserialise assignmentPlan graph: $e")
      }
    }

    "return an assignmentEntry based on a RDF representation" in {
      val expectedAssignmentEntry = PointedGraph[Rdf](URI("#"), assignmentEntryGraph).as[SesameAssignmentEntry]

      expectedAssignmentEntry match {
        case Success(s) =>
          s shouldEqual assignmentEntry
        case Failure(e) =>
          fail(s"Unable to deserialise assignmentEntry graph: $e")
      }
    }
  }
}
