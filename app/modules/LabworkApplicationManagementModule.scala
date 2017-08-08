package modules

import controllers.{LabworkApplicationCRUDController, LabworkApplicationControllerPostgres}
import dao.{LabworkApplicationDao, LabworkApplicationDaoImpl}
import services.{LabworkApplicationService, LabworkApplicationServiceLike}
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

// POSTGRES

trait LabworkApplicationDaoModule { self: DatabaseModule =>
  def labworkApplicationDao: LabworkApplicationDao
}

trait DefaultLabworkApplicationDaoModule extends LabworkApplicationDaoModule { self: DatabaseModule =>
  override lazy val labworkApplicationDao = new LabworkApplicationDaoImpl(db)
}

trait LabworkApplicationManagementModulePostgres {
  self: AuthorityDaoModule with SessionRepositoryModule with LabworkApplicationDaoModule =>

  def labworkApplicationControllerPostgres: LabworkApplicationControllerPostgres
}

trait DefaultLabworkApplicationManagementModulePostgres extends LabworkApplicationManagementModulePostgres {
  self: AuthorityDaoModule with SessionRepositoryModule with LabworkApplicationDaoModule =>

  override lazy val labworkApplicationControllerPostgres: LabworkApplicationControllerPostgres = new LabworkApplicationControllerPostgres(sessionService, authorityDao, labworkApplicationDao)
}