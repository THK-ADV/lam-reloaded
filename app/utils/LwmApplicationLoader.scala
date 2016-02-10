package utils

import controllers._
import modules._
import modules.schedule._
import modules.security._
import modules.semester._
import modules.store._
import modules.user.{DefaultEmployeeManagementModuleImpl, EmployeeManagementModule, DefaultStudentManagementModuleImpl, StudentManagementModule}
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext}
import router.Routes

class LwmApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = {
    new DefaultLwmApplication(context).application
  }
}

trait DefaultHomepageModuleImpl extends HomepageModule {
  lazy val homepageController = new HomepageController
}

trait HomepageModule {
  def homepageController: HomepageController
}

trait AssetsModule {
  self: LwmApplication =>
  def assetsController: Assets
}

trait DefaultAssetsModuleImpl extends AssetsModule {
  self: LwmApplication =>
  lazy val assetsController = new Assets(httpErrorHandler)
}

abstract class LwmApplication(context: Context) extends BuiltInComponentsFromContext(context)
with ConfigurationModule
with BaseNamespace
with HomepageModule
with SemanticRepositoryModule
with DegreeManagementModule
with CourseManagementModule
with EmployeeManagementModule
with GroupServiceManagementModule
with GroupManagementModule
with LabworkManagementModule
with RoomManagementModule
with SemesterManagementModule
with StudentManagementModule
with SessionRepositoryModule
with SecurityManagementModule
with RoleManagementModule
with RefRoleManagementModule
with AuthorityManagementModule
with PermissionManagementModule
with SessionControllerModule
with AkkaActorSystemModule
with AssetsModule
with EntryTypeManagementModule
with ResolversModule
with CORSFilterModule
with ApiDataModule
with LabworkApplicationManagementModule
with LabworkApplicationServiceModule
with ScheduleManagementModule
with TimetableManagementModule
with TimetableServiceManagementModule
with ScheduleServiceManagementModule
with BlacklistManagementModule
with BlacklistServiceManagementModule {

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(corsFilter(context.initialConfiguration))

  lazy val router: Router = new Routes(
    httpErrorHandler,
    homepageController,
    degreeManagementController,
    courseManagementController,
    employeeManagementController,
    groupManagementController,
    labworkManagementController,
    entryTypeController,
    roomManagementController,
    semesterManagementController,
    studentManagementController,
    roleManagementController,
    refRoleManagementController,
    authorityManagementController,
    permissionManagementController,
    labworkApplicationController,
    scheduleManagementController,
    timetableManagementController,
    blacklistManagementController,
    sessionController,
    apiDataController,
    assetsController
  )
}

class DefaultLwmApplication(context: Context) extends LwmApplication(context)
with ConfigurationModuleImpl
with ConfigurableBaseNamespace
with DefaultSemanticRepositoryModuleImpl
with DefaultHomepageModuleImpl
with DefaultDegreeManagementModuleImpl
with DefaultCourseManagementModuleImpl
with DefaultEmployeeManagementModuleImpl
with DefaultGroupServiceManagementModule
with DefaultGroupManagementModuleImpl
with DefaultLabworkManagementModuleImpl
with DefaultRoomManagementModuleImpl
with DefaultSemesterManagementModuleImpl
with DefaultStudentManagementModuleImpl
with LDAPModuleImpl
with DefaultSessionRepositoryModuleImpl
with DefaultAssetsModuleImpl
with DefaultRoleManagementModule
with DefaultRefRoleManagementModuleImpl
with DefaultAuthorityManagementModuleImpl
with DefaultPermissionManagementModule
with DefaultSessionControllerModuleImpl
with DefaultSecurityManagementModule
with DefaultEntryTypeManagementModule
with DefaultResolversModule
with DefaultCORSFilterModule
with DefaultApiDataModule
with DefaultLabworkApplicationManagementModule
with DefaultLabworkApplicationServiceModule
with DefaultScheduleManagementModuleImpl
with DefaultTimetableManagementModuleImpl
with DefaultTimetableServiceManagementModule
with DefaultScheduleServiceManagementModule
with DefaultBlacklistManagementModuleImpl
with DefaultBlacklistServiceManagementModule