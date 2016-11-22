package services

import java.util.UUID

import models._
import org.joda.time._
import utils.{Gen, Genesis}
import utils.TypeClasses._

import scala.language.higherKinds
import scala.util.Random._
import scalaz.Functor
import services.ScheduleService._
import models.TimetableDateEntry._
import utils.Ops.FunctorInstances.setF
import utils.Ops.MonoidInstances.intM
import utils.Evaluation._

case class Conflict(entry: ScheduleEntryG, members: Vector[UUID], group: Group)
case class ScheduleG(labwork: UUID, entries: Vector[ScheduleEntryG], id: UUID)
case class ScheduleEntryG(start: LocalTime, end: LocalTime, date: LocalDate, room: UUID, supervisor: Set[UUID], group: Group)

trait ScheduleServiceLike {
  def population(times: Int, labwork: UUID, entries: Vector[TimetableDateEntry], groups: Set[Group]): Vector[ScheduleG]
  def mutate: Mutator
  def mutateDestructive: Mutator
  def crossover: Crossover
  def crossoverDestructive: Crossover
  def evaluation(all: Vector[ScheduleG], appointments: Int): Evaluator

  def pops: Int
  def gens: Int
  def elite: Int
}

trait ScheduleGenesisServiceLike {
  def generate(timetable: Timetable, groups: Set[Group], assignmentPlan: AssignmentPlan, semester: Semester, competitive: Vector[ScheduleG], p: Option[Int] = None, g: Option[Int] = None, e: Option[Int] = None): (Gen[ScheduleG, Conflict, Int], Int)
  def competitive(labwork: Option[LabworkAtom], all: Set[ScheduleAtom]): Set[ScheduleG]
}

object ScheduleService {
  type Mutator = Mutate[ScheduleG, Conflict, Int]
  type Crossover = Cross[ScheduleG, Conflict, Int]
  type Evaluator = Eval[ScheduleG, Conflict, Int]
  type Evaluation = utils.Evaluation[Conflict, Int]

  def mutation(f: (ScheduleG, Evaluation) => ScheduleG) = Mutate.instance[ScheduleG, Conflict, Int](f)

  def cross(f: ((ScheduleG, Evaluation), (ScheduleG, Evaluation)) => (ScheduleG, ScheduleG)) = Cross.instance[ScheduleG, Conflict, Int](f)

  def eval(f: ScheduleG => Evaluation)= Eval.instance[ScheduleG, Conflict, Int](f)

  def swap[A, F[X]](f: F[A])(left: A, right: A)(implicit F: Functor[F]): F[A] = F.map(f) {
    case x if x == left => right
    case y if y == right => left
    case z => z
  }

  def randomOne[A](v: Vector[A]): A = shuffle(v).head

  @annotation.tailrec
  def randomAvoiding(avoiding: Group)(implicit groups: Vector[Group]): Group = {
    val grp = randomGroup
    if(grp.id == avoiding.id) randomAvoiding(avoiding)
    else grp
  }

  def randomGroup(implicit groups: Vector[Group]): Group = groups(nextInt(groups.size))

  def replaceGroup(s: Group)(f: Set[UUID] => Set[UUID]): Group = Group(s.label, s.labwork, f(s.members), s.invalidated, s.id)

  def replaceSchedule(s: ScheduleG)(f: ScheduleEntryG => ScheduleEntryG): ScheduleG = ScheduleG(s.labwork, s.entries map f, s.id)

  def replaceEntry(e: ScheduleEntryG)(f: Group => Group) = ScheduleEntryG(e.start, e.end, e.date, e.room, e.supervisor, f(e.group))

  def replaceWithin(s: ScheduleG)(left: Group, right: Group): ScheduleG = replaceSchedule(s)(
    replaceEntry(_) {
      case x if x.id == left.id => right
      case y if y.id == right.id => left
      case z => z
    }
  )

  def collide(left: ScheduleEntryG, right: ScheduleEntryG): Boolean = {
    val leftSlot = new Interval(left.date.toDateTime(left.start), left.date.toDateTime(left.end))
    val rightSlot = new Interval(right.date.toDateTime(right.start), right.date.toDateTime(right.end))

    leftSlot overlaps rightSlot
  }

  def exchange(left: UUID, right: UUID, s: ScheduleG) = replaceSchedule(s)(replaceEntry(_)(replaceGroup(_)(swap(_)(left, right))))
}

class ScheduleService(val pops: Int, val gens: Int, val elite: Int, private val timetableService: TimetableServiceLike) extends ScheduleServiceLike with ScheduleGenesisServiceLike {

  override def generate(timetable: Timetable, groups: Set[Group], assignmentPlan: AssignmentPlan, semester: Semester, competitive: Vector[ScheduleG], p: Option[Int], g: Option[Int], e: Option[Int]): (Gen[ScheduleG, Conflict, Int], Int) = {
    val entries = timetableService.extrapolateTimetableByWeeks(timetable, Weeks.weeksBetween(semester.start, semester.examStart), assignmentPlan, groups)
    val pop = population(p getOrElse pops, timetable.labwork, entries, groups)

    implicit val evalF = evaluation(competitive, assignmentPlan.entries.size)
    implicit val mutateF = (mutate, mutateDestructive)
    implicit val crossF = (crossover, crossoverDestructive)
    import utils.TypeClasses.instances._

    val gen = Genesis.byVariation[ScheduleG, Conflict, Int](pop, g getOrElse gens, e getOrElse elite) { elite =>
      if (elite.size % 2 == 0) elite.take(2).distinct.size == 1 else false
    }
    println(s"genesis :: ${gen._2}")
    gen
  }

  override def population(times: Int, labwork: UUID, entries: Vector[TimetableDateEntry], groups: Set[Group]): Vector[ScheduleG] = {
    (0 until times).map(_ => populate(labwork, entries, groups)).toVector
  }

  private def populate(labwork: UUID, entries: Vector[TimetableDateEntry], groups: Set[Group]): ScheduleG = {
    import models.LwmDateTime.localDateTimeOrd

    val shuffled = shuffle(groups.toVector)
    val scheduleEntries = entries.sortBy(toLocalDateTime).grouped(groups.size).flatMap(_.zip(shuffled).map {
      case (t, group) => ScheduleEntryG(t.start, t.end, t.date, t.room, t.supervisor, group)
    }).toVector

    ScheduleG(labwork, scheduleEntries, Schedule.randomUUID)
  }

  override def mutate: Mutator = mutation { (s, e) =>
    implicit val groups = s.entries.map(_.group)
    val group1 = randomGroup
    val group2 = randomAvoiding(group1)
    replaceWithin(s)(group1, group2)
  }

  override def mutateDestructive: Mutator = mutation { (s, e) =>
    implicit val groups = s.entries.map(_.group)
    e.mapErrWhole(shuffle(_)).fold {
      case ((h :: t, _)) =>
        val group = randomAvoiding(h.group)
        val chosenOne = randomOne(h.members)
        val swappedOne = randomOne(group.members.toVector)
        exchange(chosenOne, swappedOne, s)
      case ((Nil, _)) => s
    }
  }

  override def crossover: Crossover = cross {
    case ((s1, e1), (s2, e2)) =>
      (shuffle(e1.err), shuffle(e2.err)) match {
        case (h1 :: _, h2 :: _) =>
          lazy val rl = replaceWithin(s1)(h1.group, randomAvoiding(h1.group)(s1.entries.map (_.group)))
          lazy val rr = replaceWithin(s2)(h2.group, randomAvoiding(h2.group)(s2.entries.map (_.group)))
          (rl, rr)
        case _ => (s1, s2)
      }
  }

  override def crossoverDestructive: Crossover = cross {
    case ((s1, e1), (s2, e2)) =>
      def newOne(ev: Evaluation, left: ScheduleG, right: ScheduleG): ScheduleG = ev.mapErrWhole(shuffle(_)).fold {
        case ((c :: t), _) =>
          val one = randomOne(c.members)
          right.entries.find(e => !e.group.members.contains(one)) match {
            case Some(e) => exchange(one, e.group.members.head, left)
            case None =>
              val ex = randomGroup(right.entries.map (_.group)).members.head
              exchange(one, ex, left)
          }
        case ((Nil, _)) => left
      }

      (newOne(e1, s1, s2), newOne(e2, s2, s1))
  }

  override def evaluation(all: Vector[ScheduleG], appointments: Int): Evaluator = eval { schedule =>
    val factor = {
      val integrity = schedule.entries.groupBy(_.group).forall(t => t._2.size == appointments)
      if (integrity) 0 else 1000
    }

    val conflicts = for {
      globalEntry <- all flatMap (_.entries)
      entries = schedule.entries
      collision = entries find (collide(globalEntry, _))
      intersection = collision map (_.group.members intersect globalEntry.group.members)
    } yield for {
      entry <- collision
      members <- intersection if members.nonEmpty
    } yield Conflict(entry, members.toVector, entry.group)

    conflicts.foldLeft(withValue[Conflict, Int](factor)) {
      case (eval, Some(c)) => eval add c map (_ + c.members.size)
      case (eval, _) => eval
    } map (_ * conflicts.count(_.isDefined))
  }

  override def competitive(labwork: Option[LabworkAtom], all: Set[ScheduleAtom]): Set[ScheduleG] = {
    labwork.fold(Set.empty[ScheduleG]) { item =>
      val filtered = all
        .filter(_.labwork.course.semesterIndex == item.course.semesterIndex)
        .filter(_.labwork.semester.id == item.semester.id)
        .filter(_.labwork.degree.id == item.degree.id)
        .filterNot(_.labwork.id == item.id)

      filtered map { atom =>
        ScheduleG(atom.labwork.id, atom.entries.map(e => ScheduleEntryG(e.start, e.end, e.date, e.room.id, e.supervisor map (_.id), e.group)).toVector, atom.id)
      }
    }
  }
}