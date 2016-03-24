package models.security

/*
  missing permissions directly implicate either admin or god and are therefore not modelled
 */
object Permissions {

  val god = Permission("with great power comes great responsibility")

  val prime = Permission("Prime")

  object room {
    val getAll = Permission("Room:getAll")
    val get = Permission("Room:get")

    lazy val all = Set(getAll, get)
  }

  object degree {
    val getAll = Permission("Degree:getAll")
    val get = Permission("Degree:get")

    lazy val all = Set(getAll, get)
  }

  object course {
    val create = Permission("Course:create")
    val update = Permission("Course:update")
    val getAll = Permission("Course:getAll")
    val get = Permission("Course:get")

    lazy val all = Set(create, update, getAll, get)
  }

  object labwork {
    val create = Permission("Labwork:create")
    val update = Permission("Labwork:update")
    val getAll = Permission("Labwork:getAll")
    val get = Permission("Labwork:get")

    lazy val all = Set(create, update, getAll, get)
  }

  object labworkApplication {
    val create = Permission("LabworkApplication:create")
    val update = Permission("LabworkApplication:update")
    val delete = Permission("LabworkApplication:delete")
    val get = Permission("LabworkApplication:get")
    val getAll = Permission("LabworkApplication:getAll")

    lazy val all = Set(create, update, delete, get, getAll)
  }

  object authority {
    val update = Permission("Authority:update")
    val getAll = Permission("Authority:getAll")
    val get = Permission("Authority:get")

    lazy val all = Set(update, getAll, get)
  }

  object refRole {
    val getAll = Permission("RefRole:getAll")
    val get = Permission("RefRole:get")

    lazy val all = Set(getAll, get)
  }

  object role {
    val getAll = Permission("Role:getAll")
    val get = Permission("Role:get")

    lazy val all = Set(getAll, get)
  }

  object schedule {
    val create = Permission("Schedule:create")
    val update = Permission("Schedule:update")
    val delete = Permission("Schedule:delete")
    val get = Permission("Schedule:get")
    val getAll = Permission("Schedule:getAll")

    lazy val all = Set(create, update, delete, get, getAll)
  }

  object timetable {
    val create = Permission("Timetable:create")
    val update = Permission("Timetable:update")
    val delete = Permission("Timetable:delete")
    val get = Permission("Timetable:get")
    val getAll = Permission("Timetable:getAll")

    lazy val all = Set(create, update, delete, get, getAll)
  }

  object semester {
    val get = Permission("Semester:get")
    val getAll = Permission("Semester:getAll")

    lazy val all = Set(getAll, get)
  }

  object group {
    val create = Permission("Group:create")
    val update = Permission("Group:update")
    val delete = Permission("Group:delete")
    val get = Permission("Group:get")
    val getAll = Permission("Group:getAll")

    lazy val all = Set(create, update, delete, get, getAll)
  }

  object user {
    val get = Permission("User:get")
    val getAll = Permission("User:getAll")

    lazy val all = Set(get, getAll)
  }

  object blacklist {
    val get = Permission("Blacklist:get")

    lazy val all = Set(get)
  }

  object entryType {
    val getAll = Permission("EntryType:getAll")

    lazy val all = Set(getAll)
  }

  object reportCard {
    val get = Permission("ReportCard:get")

    lazy val all = Set(get)
  }

  object reportCardEntry {
    val update = Permission("ReportCardEntry:update")

    lazy val all = Set(update)
  }

  object reportCardEntryType {
    val update = Permission("ReportCardEntryType:update")

    lazy val all = Set(update)
  }

  object assignmentPlan {
    val create = Permission("AssignmentPlan:create")
    val update = Permission("AssignmentPlan:update")
    val delete = Permission("AssignmentPlan:delete")
    val getAll = Permission("AssignmentPlan:getAll")
    val get = Permission("AssignmentPlan:get")

    lazy val all = Set(create, update, delete, getAll, get)
  }

  lazy val all = room.all ++ degree.all ++ course.all ++
    labwork.all ++ labworkApplication.all ++ authority.all ++ refRole.all ++
    role.all ++ schedule.all ++ timetable.all ++ semester.all ++ group.all ++
    user.all ++ blacklist.all ++ entryType.all ++ reportCard.all ++ reportCardEntry.all ++
    reportCardEntryType.all + prime
}