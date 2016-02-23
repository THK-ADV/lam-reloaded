package services

import base.TestBaseDefinition
import models.semester.Blacklist
import models.schedule.{Weekday, TimetableDateEntry}
import org.joda.time.{DateTime, LocalDate, LocalTime}
import org.scalatest.WordSpec
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar.mock
import store.SesameRepository

import scala.util.Success

class BlacklistServiceSpec extends WordSpec with TestBaseDefinition {

  import scala.util.Random.nextInt
  import TimetableDateEntry._

  val repo = mock[SesameRepository]
  val blacklistService = new BlacklistService(repo)

  def toDateTime(entry: TimetableDateEntry): DateTime = {
    entry.date.toDateTime(entry.start)
  }

  "A BlacklistServiceSpec" should {

    "pass timetable entries when local and global blacklists are empty" in {
      val global = Set(Blacklist.empty)
      when(repo.get[Blacklist](anyObject(), anyObject())).thenReturn(Success(global))

      val entries = (0 until 100).map { n =>
        val date = LocalDate.now.plusWeeks(n)
        val start = LocalTime.now.withHourOfDay(nextInt(19))
        val end = start.plusHours(nextInt(3))

        TimetableDateEntry(Weekday.toDay(date), date, start, end)
      }.toSet

      val local = Blacklist.empty
      val result = blacklistService.applyBlacklist(entries, local)

      local.dates.count(l => entries.exists(_.start.isEqual(l.toLocalTime))) shouldBe local.dates.size
      global.head.dates.count(g => entries.exists(_.date.isEqual(g.toLocalDate))) shouldBe global.head.dates.size

      result.map(_.date).intersect(global.head.dates.map(_.toLocalDate)) shouldBe empty
      result.map(toLocalDateTime).intersect(local.dates.map(_.toLocalDateTime)) shouldBe empty
      result.subsetOf(entries) shouldBe true

      result shouldBe entries
      result.size shouldBe entries.size
      result.forall(a => local.dates.exists(_.toLocalDateTime.isEqual(toLocalDateTime(a)))) shouldBe false
      result.forall(a => global.head.dates.exists(_.toLocalDate.isEqual(a.date))) shouldBe false
      result.map(toLocalDateTime).toVector.sorted shouldBe sorted
    }

    "apply local and global blacklist dates on timetable entries" in {
      val entries = (0 until 100).map { n =>
        val date = LocalDate.now.plusWeeks(n)
        val start = LocalTime.now.withHourOfDay(nextInt(19))
        val end = start.plusHours(nextInt(3))

        TimetableDateEntry(Weekday.toDay(date), date, start, end)
      }.toSet

      val global1 = Blacklist(entries.slice(0, 10).map(toDateTime), Blacklist.randomUUID)
      val global2 = Blacklist(entries.slice(10, 20).map(toDateTime), Blacklist.randomUUID)
      when(repo.get[Blacklist](anyObject(), anyObject())).thenReturn(Success(Set(global1, global2)))

      val local = Blacklist(entries.slice(20, 30).map(toDateTime), Blacklist.randomUUID)

      val result = blacklistService.applyBlacklist(entries, local)

      local.dates.count(l => entries.exists(_.start.isEqual(l.toLocalTime))) shouldBe local.dates.size
      global1.dates.count(g => entries.exists(_.date.isEqual(g.toLocalDate))) shouldBe global1.dates.size
      global2.dates.count(g => entries.exists(_.date.isEqual(g.toLocalDate))) shouldBe global2.dates.size

      result.map(_.date).intersect(global1.dates.map(_.toLocalDate)) shouldBe empty
      result.map(_.date).intersect(global2.dates.map(_.toLocalDate)) shouldBe empty
      result.map(toLocalDateTime).intersect(local.dates.map(_.toLocalDateTime)) shouldBe empty
      result.subsetOf(entries) shouldBe true

      result.size should be < entries.size
      result.size shouldBe entries.size - local.dates.size - global1.dates.size - global2.dates.size
      result.forall(a => local.dates.exists(_.toLocalDateTime.isEqual(toLocalDateTime(a)))) shouldBe false
      result.forall(a => global1.dates.exists(_.toLocalDate.isEqual(a.date))) shouldBe false
      result.forall(a => global2.dates.exists(_.toLocalDate.isEqual(a.date))) shouldBe false
      result.map(toLocalDateTime).toVector.sorted shouldBe sorted
    }
  }
}