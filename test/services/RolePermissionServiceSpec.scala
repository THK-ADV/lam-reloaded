package services

import base.PostgresDbSpec
import slick.dbio.Effect.Write
import slick.driver.PostgresDriver.api._

object RolePermissionServiceSpec extends PostgresDbSpec with RolePermissionService {
  override protected def customFill: DBIOAction[Unit, NoStream, Write] = ???
}
