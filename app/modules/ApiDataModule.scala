package modules

import controllers.ApiDataController

trait ApiDataModule {
  self: SemanticRepositoryModule =>

  def apiDataController: ApiDataController
}

trait DefaultApiDataModule extends ApiDataModule {
  self: SemanticRepositoryModule with UserServiceModule with AssignmentPlanServiceModule with CourseServiceModule with DegreeServiceModule with LabworkApplication2ServiceModule
    with LabworkServiceModule with PermissionServiceModule with RoleServiceModule with RoomServiceModule with SemesterServiceModule =>

  override lazy val apiDataController: ApiDataController = new ApiDataController(repository, userService, assignmentPlanService, courseService, degreeService, labworkApplicationService2,
    labworkService, permissionService, roleService2, roomService, semesterService)
}
