package security

import controllers.helper.RequestOps
import dao.{AuthorityDao, UserDao}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.Results.{Conflict, InternalServerError}
import play.api.mvc.{ActionRefiner, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthorityAction @Inject()(authorityDao: AuthorityDao, userDao: UserDao)(implicit val executionContext: ExecutionContext)
  extends ActionRefiner[IdRequest, AuthRequest] with RequestOps {

  override protected def refine[A](request: IdRequest[A]): Future[Either[Result, AuthRequest[A]]] =
    authorityDao.authoritiesFor(request.systemId).map { authorities =>
      if (authorities.nonEmpty)
        Right(AuthRequest(request, authorities))
      else
        Left(Conflict(Json.obj(
          "status" -> "KO",
          "message" -> s"no user found with systemId ${request.systemId}"
        )))
    }.recover {
      case t => Left(error(t.getLocalizedMessage))
    }

  private def error(message: String): Result = InternalServerError(Json.obj(
    "status" -> "KO",
    "message" -> message
  ))
}
