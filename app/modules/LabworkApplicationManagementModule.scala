package modules

import controllers.{LabworkApplicationCRUDController, LabworkApplicationControllerPostgres}
import services.{LabworkApplicationService, LabworkApplicationService2, LabworkApplicationServiceLike}
import utils.LwmApplication

trait LabworkApplicationServiceModule {
  self: LwmApplication with SemanticRepositoryModule =>

  def labworkApplicationService: LabworkApplicationServiceLike
}

trait DefaultLabworkApplicationServiceModule extends LabworkApplicationServiceModule {
  self: LwmApplication with SemanticRepositoryModule =>

  override lazy val labworkApplicationService: LabworkApplicationServiceLike = LabworkApplicationService(repository)
}

trait LabworkApplicationManagementModule {
  self: SemanticRepositoryModule with SecurityManagementModule with SessionRepositoryModule =>

  def labworkApplicationController: LabworkApplicationCRUDController
}

trait DefaultLabworkApplicationManagementModule extends LabworkApplicationManagementModule {
  self: SemanticRepositoryModule with BaseNamespace with SecurityManagementModule with SessionRepositoryModule =>

  override lazy val labworkApplicationController: LabworkApplicationCRUDController = new LabworkApplicationCRUDController(repository, sessionService, namespace, roleService)
}

trait LabworkApplicationManagementModulePostgres {
  self: SecurityManagementModule with SessionRepositoryModule =>

  def labworkApplicationControllerPostgres: LabworkApplicationControllerPostgres
}

trait DefaultLabworkApplicationManagementModulePostgres extends LabworkApplicationManagementModulePostgres {
  self: SecurityManagementModule with SessionRepositoryModule =>

  override lazy val labworkApplicationControllerPostgres: LabworkApplicationControllerPostgres = new LabworkApplicationControllerPostgres(sessionService, roleService, LabworkApplicationService2)
}