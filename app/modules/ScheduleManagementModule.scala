package modules

import controllers.ScheduleController
import services._
import utils.LwmApplication

trait ScheduleServiceManagementModule {
  self: LwmApplication =>

  def scheduleService: ScheduleGenesisServiceLike
}

trait DefaultScheduleServiceManagementModule extends ScheduleServiceManagementModule {
  self: LwmApplication with TimetableServiceManagementModule =>

  lazy val scheduleService: ScheduleGenesisServiceLike = new ScheduleService(20, 100, 10, timetableService)
}

trait ScheduleManagementModule {

  def scheduleManagementController: ScheduleController
}

trait DefaultScheduleManagementModuleImpl extends ScheduleManagementModule {
  self: SemanticRepositoryModule with BaseNamespace with SecurityManagementModule with SessionRepositoryModule with ScheduleServiceManagementModule with GroupServiceManagementModule =>

  lazy val scheduleManagementController: ScheduleController = new ScheduleController(repository, sessionService, namespace, roleService, scheduleService, groupService)
}