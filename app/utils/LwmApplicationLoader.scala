package utils

import controllers._
import modules._
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
with DatabaseModule
with DbFolder
with DegreeManagementModule
with DegreeManagementModulePostgres
with DegreeDaoModule
with CourseManagementModule
with CourseManagementModulePostgres
with CourseDaoModule
with GroupServiceManagementModule
with GroupDaoManagementModule
with GroupManagementModule
with GroupManagementModule2
with LabworkManagementModule
with LabworkManagementModulePostgres
with LabworkDaoModule
with RoomManagementModule
with RoomManagementModulePostgres
with RoomDaoModule
with SemesterManagementModule
with SemesterManagementModulePostgres
with SemesterDaoModule
with SessionRepositoryModule
with SecurityManagementModule
with RoleManagementModule
with RoleManagementModulePostgres
with RoleDaoModule
with AuthorityManagementModule
with AuthorityManagementModule2
with AuthorityDaoModule
with PermissionManagementModule
with SessionControllerModule
with AkkaActorSystemModule
with LdapModule
with AssetsModule
with EntryTypeManagementModule
with ResolversModule
with CORSFilterModule
with ApiDataModule
with LabworkApplicationManagementModule
with LabworkApplicationManagementModulePostgres
with LabworkApplicationServiceModule
with LabworkApplicationDaoModule
with ScheduleManagementModule
with ScheduleEntryDaoModule
with ScheduleEntryManagementModule
with ScheduleEntryManagementModule2
with TimetableManagementModule
with TimetableManagementModulePostgres
with TimetableServiceManagementModule
with TimetableDaoManagementModule
with ScheduleServiceManagementModule
with BlacklistManagementModule
with Blacklist2ManagementModule
with BlacklistServiceManagementModule
with BlacklistDaoManagementModule
with ReportCardServiceManagementModule
with ReportCardEntryDaoModule
with ReportCardEntryManagementModule
with ReportCardEntryManagementModule2
with ReportCardEntryTypeManagementModule
with ReportCardEntryTypeDaoModule
with ReportCardEntryTypeManagementModule2
with AssignmentPlanManagementModule
with AssignmentPlanManagementModulePostgres
with AssignmentPlanDaoModule
with UserManagementModule
with UserManagementModulePostgres
with UserDaoModule
with AnnotationManagementModule
with ReportCardEvaluationManagementModule
with LdapSyncModule {
  override lazy val httpFilters: Seq[EssentialFilter] = Seq(corsFilter(context.initialConfiguration))

  lazy val router: Router = new Routes(
    httpErrorHandler,
    homepageController,
    degreeManagementController,
    degreeManagementControllerPostgres,
    courseManagementController,
    courseManagementControllerPostgres,
    groupManagementController,
    groupManagementControllerPostgres,
    labworkManagementController,
    labworControllerPostgres,
    entryTypeController,
    roomManagementController,
    roomManagementControllerPostgres,
    semesterManagementController,
    semesterManagementControllerPostgres,
    roleManagementController,
    roleManagementControllerPostgres,
    authorityManagementController,
    authorityControllerPostgres,
    permissionManagementController,
    labworkApplicationController,
    labworkApplicationControllerPostgres,
    scheduleManagementController,
    scheduleEntryController,
    scheduleEntryControllerPostgres,
    timetableManagementController,
    timetableControllerPostgres,
    blacklistManagementController,
    blacklistControllerPostgres,
    reportCardEntryManagementController,
    reportCardEntryControllerPostgres,
    reportCardEntryTypeManagementController,
    reportCardEntryTypeController,
    reportCardEvaluationManagementController,
    assignmentPlanManagementController,
    assignmentPlanManagementControllerPostgres,
    annotationManagementController,
    userController,
    userControllerPostgres,
    sessionController,
    apiDataController,
    assetsController
  )
}

class DefaultLwmApplication(context: Context) extends LwmApplication(context)
with ConfigurationModuleImpl
with ConfigurableBaseNamespace
with DefaultSemanticRepositoryModuleImpl
with DefaultDatabaseModule
with DefaultHomepageModuleImpl
with DefaultDegreeManagementModuleImpl
with DefaultDegreeManagementModuleImplPostgres
with DefaultDegreeDaoModule
with DefaultCourseManagementModuleImpl
with DefaultCourseManagementModuleImplPostgres
with DefaultCourseDaoModule
with DefaultGroupServiceManagementModule
with DefaultGroupDaoManagementModule
with DefaultGroupManagementModuleImpl
with DefaultGroupManagementModule2
with DefaultLabworkManagementModuleImpl
with DefaultLabworkManagementModulePostgres
with DefaultLabworkDaoModule
with DefaultRoomManagementModuleImpl
with DefaultRoomManagementModuleImplPostgres
with DefaultRoomDaoModule
with DefaultSemesterManagementModuleImpl
with DefaultSemesterManagementModuleImplPostgres
with DefaultSemesterDaoModule
with LdapModuleImpl
with DefaultSessionRepositoryModuleImpl
with DefaultAssetsModuleImpl
with DefaultRoleManagementModule
with DefaultRoleManagementModulePostgres
with DefaultRoleDaoModule
with DefaultAuthorityManagementModuleImpl
with DefaultAuthorityManagementModule2
with DefaultAuthorityDaoModule
with DefaultPermissionManagementModule
with DefaultSessionControllerModuleImpl
with DefaultSecurityManagementModule
with DefaultEntryTypeManagementModule
with DefaultResolversModule
with DefaultCORSFilterModule
with DefaultApiDataModule
with DefaultLabworkApplicationManagementModule
with DefaultLabworkApplicationManagementModulePostgres
with DefaultLabworkApplicationServiceModule
with DefaultLabworkApplicationDaoModule
with DefaultScheduleManagementModuleImpl
with DefaultScheduleEntryDaoModule
with DefaultScheduleEntryManagementModule
with DefaultScheduleEntryManagementModule2
with DefaultTimetableManagementModuleImpl
with DefaultTimetableManagementModulePostgres
with DefaultTimetableServiceManagementModule
with DefaultTimetableDaoModule
with DefaultScheduleServiceManagementModule
with DefaultBlacklistManagementModuleImpl
with DefaultBlacklist2ManagementModule
with DefaultBlacklistServiceManagementModule
with DefaultBlacklistDaoManagementModule
with DefaultReportCardServiceManagementModule
with DefaultReportCardEntryDaoModule
with DefaultReportCardEntryManagementModuleImpl
with DefaultReportCardEntryManagementModule2
with DefaultReportCardEntryTypeManagementModuleImpl
with DefaultReportCardEntryTypeDaoModule
with DefaultReportCardEntryTypeManagementModule2
with DefaultAssignmentPlanManagementModuleImpl
with DefaultAssignmentPlanManagementModuleImplPostgres
with DefaultAssignmentPlanDaoModule
with DefaultUserManagementModule
with DefaultUserManagementModulePostgres
with DefaultUserDaoModule
with DefaultAnnotationManagementModuleImpl
with DefaultReportCardEvaluationManagementModuleImpl
with DefaultDbFolderImpl
with DefaultDbBackupModuleImpl
with DefaultLdapSyncService