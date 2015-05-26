package controllers

import models.{UniqueEntity, UriGenerator}
import org.w3.banana.binder.{ClassUrisFor, FromPG, ToPG}
import org.w3.banana.sesame.{Sesame, SesameModule}
import org.w3.banana.{RDFModule, RDFOpsModule}
import play.api.libs.json.{JsError, Json, Reads, Writes}
import play.api.mvc.{Action, Controller}
import store.bind.Bindings
import store.{SesameRepository, Namespace, SemanticRepository}
import utils.Global

import scala.util.{Failure, Success}


trait SesameRdfSerialisation[T <: UniqueEntity] {
  def baseNS: Namespace

  def repository: SesameRepository

  def defaultBindings: Bindings[Sesame] = Bindings[Sesame](baseNS)

  implicit def rdfWrites: ToPG[Sesame, T]

  implicit def rdfReads: FromPG[Sesame, T]

  implicit def classUrisFor: ClassUrisFor[Sesame, T]

  implicit def uriGenerator: UriGenerator[T]
}


trait JsonSerialisation[T] {
  implicit def reads: Reads[T]

  implicit def writes: Writes[T]
}

abstract class AbstractCRUDController[T <: UniqueEntity] extends Controller
with  JsonSerialisation[T] with SesameRdfSerialisation[T] {

  override def repository: SesameRepository = Global.repo

  override def baseNS: Namespace = Global.namespace

  // POST /Ts
  def create() = Action(parse.json) { implicit request =>
    request.body.validate[T].fold(
      errors => {
        BadRequest(Json.obj(
          "status" -> "KO",
          "errors" -> JsError.toFlatJson(errors)
        ))
      },
      success => {
        repository.add[T](success)(rdfWrites) match {
          case Success(graph) =>
            Created(Json.obj(
              "status" -> "OK",
              "id" -> graph.toString //TODO: determine resource id
            ))
          case Failure(e) =>
            InternalServerError(Json.obj(
              "status" -> "KO",
              "errors" -> Seq(e.toString)
            ))
        }
      }
    )
  }

  // GET /Ts/:id
  def get(id: String) = Action { implicit request =>
    repository.get[T](id) match {
      case Success(s) =>
        s match {
          case Some(entity) =>
            Ok(Json.toJson(entity))
          case None =>
            NotFound(Json.obj(
              "status" -> "KO",
              "message" -> "No such element..."
            ))
        }
      case Failure(e) =>
        InternalServerError(Json.obj(
          "status" -> "KO",
          "errors" -> Seq(e.toString)
        ))
    }
  }

  // GET /ts
  def all() = Action { implicit request =>
    repository.get[T] match {
      case Success(s) =>
        Ok(Json.toJson(s))
      case Failure(e) =>
        InternalServerError(Json.obj(
          "status" -> "KO",
          "errors" -> Seq(e.toString)
        ))
    }
  }

  def update(id: String) = Action { implicit request =>
    repository.get[T](id) match {
      case Success(s) =>
        s match {
          case Some(t) =>
            repository.update[T, UriGenerator[T]](t) match {
              case Success(m) =>
                Ok(Json.obj(
                  "status" -> "OK",
                  "id" -> m.toString //TODO: determine subject/resource id
                ))
              case Failure(e) =>
                InternalServerError(Json.obj(
                  "status" -> "KO",
                  "errors" -> Seq(e.toString)
                ))
            }
          case None =>
            NotFound(Json.obj(
              "status" -> "KO",
              "message" -> "No such element..."
            ))
        }
      case Failure(e) =>
        InternalServerError(Json.obj(
          "status" -> "KO",
          "errors" -> Seq(e.toString)
        ))
    }
  }

  def delete(id: String) = ???
}
