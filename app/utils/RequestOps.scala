package utils

import play.api.mvc.Request

object RequestOps {

  def rebase[A](uri: String)(implicit request: Request[A]): Request[A] = {
    val headers = request.copy(request.id, request.tags, uri)
    Request(headers, request.body)
  }

  def rebase[A](uri: String, query: (String, Seq[String])*)(implicit request: Request[A]): Request[A] = {
    val queryString = query.foldLeft(request.queryString)(_ + _)
    val headers = request.copy(request.id, request.tags, uri, request.path, request.method, request.version, queryString)
    Request(headers, request.body)
  }
}
