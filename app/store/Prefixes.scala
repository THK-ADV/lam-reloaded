package store

import models.AssignmentEntryType
import org.w3.banana.{RDF, RDFOps}

object Prefixes {

  class LWMPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("lwm", "http://lwm.fh-koeln.de/ns/")(ops) {

    // xsd extensions
    val localDate = apply("localDate")
    val localTime = apply("localDate")

    // _
    val id = apply("id")

    //Labwork, Course, Degree, Group, Room, Semester, AssignmentEntry, ReportCardEntry
    val label = apply("label")

    // Labwork, LabworkApplication, Room, Degree, TimetableEntry
    val description = apply("description")
    val assignmentPlan = apply("assignmentPlan")
    val semester = apply("semester")
    val course = apply("course")
    val degree = apply("degree")
    val applicant = apply("applicant")
    val timestamp = apply("timestamp")
    val friends = apply("friends")

    // Student, Employee, User
    val email = apply("email")
    val firstname = apply("firstname")
    val lastname = apply("lastname")
    val registrationId = apply("registrationId")
    val systemId = apply("systemId")
    val enrollment = apply("enrollment")
    val status = apply("status")

    // Semester, ReportCardEntry
    val end = apply("start")
    val start = apply("end")
    val examStart = apply("examStart")

    // AssignmentEntry, AssignmentPlan, Timetable, Schedule, ReportCardEntry
    val index = apply("index")
    val duration = apply("duration")
    val types = apply("types")
    val entries = apply("entries")
    val attendance = apply("attendance")
    val mandatory = apply("mandatory")

    // AssignmentPlanEntry
    val entryType = apply("entryType")
    val bool = apply("bool")
    val int = apply("int")

    // Blacklist
    val dates = apply("dates")

    // Course
    val abbreviation = apply("abbreviation")
    val lecturer = apply("lecturer")
    val semesterIndex = apply("semesterIndex")

    // RefRole, Role, Authority
    val name = apply("name")
    val role = apply("role")
    val refroles = apply("refRoles")
    val permissions = apply("permissions")
    val module = apply("module")
    val privileged = apply("privileged")

    //Group, Timetable, Schedule, ReportCard
    val members = apply("members")
    val labwork = apply("labwork")

    // Timetable
    val blacklist = apply("blacklist")
    val buffer = apply("buffer")

    // TimetableEntry, ScheduleEntry, ReportCardEntry
    val supervisor = apply("supervisor")
    val room = apply("room")
    val dayIndex = apply("dayIndex")
    val date = apply("date")

    // ScheduleEntry
    val group = apply("group")

    // Schedule
    val published = apply("published")

    // ReportCard
    val student = apply("student")

    // classes
    val User = apply("User")
    val Course = apply("Course")
    val Degree = apply("Degree")
    //val Employee = apply("Employee")
    val Group = apply("Group")
    val Labwork = apply("Labwork")
    val Room = apply("Room")
    val Semester = apply("Semester")
    //val Student = apply("Student")
    val Role = apply("Role")
    val RefRole = apply("RefRole")
    val Authority = apply("Authority")
    val AssignmentPlan = apply("AssignmentPlan")
    val AssignmentEntry = apply("AssignmentEntry")
    val AssignmentEntryType = apply("AssignmentEntryType")
    val LabworkApplication = apply("LabworkApplication")
    val Timetable = apply("Timetable")
    val TimetableEntry = apply("TimetableEntry")
    val Schedule = apply("Schedule")
    val ScheduleEntry = apply("ScheduleEntry")
    val Blacklist = apply("Blacklist")
    val ReportCard = apply("ReportCard")
    val ReportCardEntry = apply("ReportCardEntry")
  }

  object LWMPrefix {
    def apply[Rdf <: RDF : RDFOps](implicit ops: RDFOps[Rdf]) = new LWMPrefix(ops)
  }

}
