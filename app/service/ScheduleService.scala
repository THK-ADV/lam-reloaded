package service

import java.util.UUID

import javax.inject.Inject
import models.{genesis, _}
import models.genesis.{Conflict, ScheduleEntryGen, ScheduleGen}
import models.helper.TimetableDateEntry
import org.joda.time._
import scalaz.Functor
import service.ScheduleService.{Crossover, Evaluator, Mutator}
import utils.Evaluation._
import utils.date.DateTimeOps._
import utils.Ops.FunctorInstances.setF
import utils.Ops.MonoidInstances.intM
import utils.TypeClasses.{Cross, Eval, Mutate}
import utils.{Gen, Genesis}

import scala.language.higherKinds
import scala.util.Random._

trait ScheduleService {
  def population(times: Int, labwork: UUID, entries: Vector[TimetableDateEntry], groups: Vector[Group]): Vector[ScheduleGen]

  def mutate: Mutator

  def mutateDestructive: Mutator

  def crossover: Crossover

  def crossoverDestructive: Crossover

  def evaluation(all: Vector[ScheduleGen], appointments: Int): Evaluator

  def pops: Int

  def gens: Int

  def elite: Int

  def generate(
    labwork: UUID,
    ts: Vector[TimetableEntry],
    start: LocalDate,
    bs: Vector[Blacklist],
    gs: Vector[Group],
    aps: Vector[AssignmentEntry],
    s: Semester,
    cs: Vector[ScheduleGen],
    p: Option[Int] = None,
    g: Option[Int] = None,
    e: Option[Int] = None
  ): (Gen[ScheduleGen, Conflict, Int], Int)
}

object ScheduleService {
  type Mutator = Mutate[ScheduleGen, Conflict, Int]
  type Crossover = Cross[ScheduleGen, Conflict, Int]
  type Evaluator = Eval[ScheduleGen, Conflict, Int]
  type Evaluation = utils.Evaluation[Conflict, Int]

  def mutation(f: (ScheduleGen, Evaluation) => ScheduleGen) = Mutate.instance[ScheduleGen, Conflict, Int](f)

  def cross(f: ((ScheduleGen, Evaluation), (ScheduleGen, Evaluation)) => (ScheduleGen, ScheduleGen)) = Cross.instance[ScheduleGen, Conflict, Int](f)

  def eval(f: ScheduleGen => Evaluation) = Eval.instance[ScheduleGen, Conflict, Int](f)

  def swap[A, F[X]](f: F[A])(left: A, right: A)(implicit F: Functor[F]): F[A] = F.map(f) {
    case x if x == left => right
    case y if y == right => left
    case z => z
  }

  def randomOne[A](v: Vector[A]): A = shuffle(v).head

  @annotation.tailrec
  def randomAvoiding(avoiding: Group)(implicit groups: Vector[Group]): Group = {
    if (groups.forall(_.id == avoiding.id)) // can't be another group than avoiding. just return it then
      avoiding
    else { // try to get another group than avoiding
      val grp = randomGroup
      if (grp.id == avoiding.id) randomAvoiding(avoiding) else grp
    }
  }

  def randomGroup(implicit groups: Vector[Group]): Group = groups(nextInt(groups.size))

  def replaceGroup(s: Group)(f: Set[UUID] => Set[UUID]): Group = Group(s.label, s.labwork, f(s.members), s.id)

  def replaceSchedule(s: ScheduleGen)(f: ScheduleEntryGen => ScheduleEntryGen): ScheduleGen = genesis.ScheduleGen(s.labwork, s.entries map f)

  def replaceEntry(e: ScheduleEntryGen)(f: Group => Group) = ScheduleEntryGen(e.start, e.end, e.date, e.room, e.supervisor, f(e.group))

  def replaceWithin(s: ScheduleGen)(left: Group, right: Group): ScheduleGen = replaceSchedule(s)(
    replaceEntry(_) {
      case x if x.id == left.id => right
      case y if y.id == right.id => left
      case z => z
    }
  )

  def collide(left: ScheduleEntryGen, right: ScheduleEntryGen): Boolean = {
    val leftSlot = new Interval(left.date.toDateTime(left.start), left.date.toDateTime(left.end))
    val rightSlot = new Interval(right.date.toDateTime(right.start), right.date.toDateTime(right.end))

    leftSlot overlaps rightSlot
  }

  def exchange(left: UUID, right: UUID, s: ScheduleGen) = replaceSchedule(s)(replaceEntry(_)(replaceGroup(_)(swap(_)(left, right))))
}

final class ScheduleServiceImpl @Inject()(val pops: Int, val gens: Int, val elite: Int) extends ScheduleService {

  import service.ScheduleService._

  override def generate(
    labwork: UUID,
    ts: Vector[TimetableEntry],
    start: LocalDate,
    bs: Vector[Blacklist],
    gs: Vector[Group],
    aps: Vector[AssignmentEntry],
    s: Semester,
    cs: Vector[ScheduleGen],
    p: Option[Int],
    g: Option[Int],
    e: Option[Int]
  ) = {
    val entries = TimetableService.extrapolateTimetableByWeeks(ts, start, Weeks.weeksBetween(s.start, s.examStart), bs, aps, gs.size)
    val pop = population(p getOrElse pops, labwork, entries, gs)

    implicit val evalF: Evaluator = evaluation(cs, aps.size)
    implicit val mutateF: (Mutator, Mutator) = (mutate, mutateDestructive)
    implicit val crossF: (Crossover, Crossover) = (crossover, crossoverDestructive)
    import utils.TypeClasses.instances._

    Genesis.byVariation[ScheduleGen, Conflict, Int](pop, g getOrElse gens, e getOrElse elite) { elite =>
      if (elite.size % 2 == 0) elite.take(2).distinct.size == 1 else false
    }
  }

  override def population(times: Int, labwork: UUID, entries: Vector[TimetableDateEntry], groups: Vector[Group]): Vector[ScheduleGen] = {
    (0 until times).map(_ => populate(labwork, entries, groups)).toVector
  }

  private def populate(labwork: UUID, entries: Vector[TimetableDateEntry], groups: Vector[Group]): ScheduleGen = {
    val shuffled = shuffle(groups)
    val scheduleEntries = entries.sortBy(toLocalDateTime).grouped(groups.size).flatMap(_.zip(shuffled).map {
      case (t, group) => ScheduleEntryGen(t.start, t.end, t.date, t.room, t.supervisor, group)
    }).toVector

    genesis.ScheduleGen(labwork, scheduleEntries)
  }

  override def mutate: Mutator = mutation { (s, _) =>
    implicit val groups: Vector[Group] = s.entries.map(_.group)

    val group1 = randomGroup
    val group2 = randomAvoiding(group1)

    replaceWithin(s)(group1, group2)
  }

  override def mutateDestructive: Mutator = mutation { (s, e) =>
    implicit val groups: Vector[Group] = s.entries.map(_.group)

    e.mapErrWhole(shuffle(_)).fold {
      case ((h :: _, _)) =>
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
          lazy val rl = replaceWithin(s1)(h1.group, randomAvoiding(h1.group)(s1.entries.map(_.group)))
          lazy val rr = replaceWithin(s2)(h2.group, randomAvoiding(h2.group)(s2.entries.map(_.group)))

          (rl, rr)
        case _ => (s1, s2)
      }
  }

  override def crossoverDestructive: Crossover = cross {
    case ((s1, e1), (s2, e2)) =>
      def newOne(ev: Evaluation, left: ScheduleGen, right: ScheduleGen): ScheduleGen = ev.mapErrWhole(shuffle(_)).fold {
        case ((c :: _), _) =>
          val one = randomOne(c.members)

          right.entries.find(e => !e.group.members.contains(one)) match {
            case Some(e) => exchange(one, e.group.members.head, left)
            case None =>
              val ex = randomGroup(right.entries.map(_.group)).members.head
              exchange(one, ex, left)
          }
        case ((Nil, _)) => left
      }

      (newOne(e1, s1, s2), newOne(e2, s2, s1))
  }

  override def evaluation(all: Vector[ScheduleGen], appointments: Int): Evaluator = eval { schedule =>
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
}