package utils

import java.util.UUID
import java.util.concurrent.Executors

import auth.{OAuthAuthorization, UserToken}
import controllers.helper.RequestOps
import dao.{AuthorityDao, UserDao}
import javax.inject.{Inject, Singleton}
import models.{Authority, LWMRole, Role}
import play.api.libs.json.Json
import play.api.mvc.Results.{Forbidden, Unauthorized}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
final class SecuredAction @Inject()(authenticated: Authenticated, authorityDao: AuthorityDao, userDao: UserDao) extends RequestOps {

  private implicit val actionExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  private def securedAction(predicate: Seq[Authority] => Future[Boolean]) = {
    authenticated andThen authorized andThen allowed(predicate)
  }

  def secured[R <: LWMRole](ps: (Option[UUID], List[R]))(block: Request[AnyContent] => Result) = {
    securedAction(authorities => authorityDao.checkAuthority(ps)(authorities)).apply(block)
  }

  def securedAsync[R <: LWMRole](ps: (Option[UUID], List[R]))(block: Request[AnyContent] => Future[Result]) = {
    securedAction(authorities => authorityDao.checkAuthority(ps)(authorities)).async(block)
  }

  private def authorized = new ActionRefiner[IdRequest, AuthRequest] {
//    override protected def refine[A](request: IdRequest[A]): Future[Either[Result, AuthRequest[A]]] = authorityDao.authoritiesFor(request.systemId).map { authorities =>
//      Either.cond(authorities.nonEmpty, AuthRequest(request, authorities), Unauthorized(Json.obj(
//        "status" -> "KO",
//        "message" -> s"No authority found for ${request.systemId}"
//      )))
//    }

    override protected def refine[A](request: IdRequest[A]): Future[Either[Result, AuthRequest[A]]] = authorityDao.authoritiesFor(request.systemId).map {
      case authorities =>
        Right(AuthRequest(request, authorities))
      case Nil =>
        val token = request.userToken.get

        ???
    }

    override protected def executionContext: ExecutionContext = actionExecutionContext
  }

  private def allowed(predicate: Seq[Authority] => Future[Boolean]) = new ActionFilter[AuthRequest] {
    override protected def filter[A](request: AuthRequest[A]): Future[Option[Result]] = predicate(request.authorities).map { allowed =>
      if (allowed) None else Some(Forbidden(Json.obj(
        "status" -> "KO",
        "message" -> "Insufficient permissions for given action"
      )))
    }

    override protected def executionContext: ExecutionContext = actionExecutionContext
  }
}

case class AuthRequest[A](private val unwrapped: Request[A], authorities: Seq[Authority]) extends WrappedRequest[A](unwrapped)

case class IdRequest[A](private val unwrapped: Request[A], systemId: String) extends WrappedRequest[A](unwrapped)

@Singleton
case class Authenticated @Inject()(auth: OAuthAuthorization)(val parser: BodyParsers.Default)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[IdRequest, AnyContent] {

  override def invokeBlock[A](request: Request[A], block: IdRequest[A] => Future[Result]) = auth.authorized(request).flatMap {
    case token: UserToken =>
      val newRequest = request.addAttr(RequestOps.UserToken, token)
      block(IdRequest(newRequest, token.systemId))
  }.recover {
    case NonFatal(e) => Unauthorized(Json.obj(
      "status" -> "KO",
      "message" -> e.getLocalizedMessage
    ))
  }
}
