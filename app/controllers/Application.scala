package controllers

import play.api.{Logger, libs}
import libs.Comet
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms.{single, nonEmptyText}
import actors.{Listen, PingActor, AddPing}
import akka.util.Timeout
import akka.util.duration._
import play.api.libs.iteratee._
import akka.pattern.ask
import play.api.libs.concurrent._

object Application extends Controller {

  val urlForm = Form(
    single("url" -> nonEmptyText)
  )

  def index = Action {
    PingActor.coordinator ! AddPing("http://www.hs.fi")
    Ok(views.html.index(urlForm))
  }

  def addUrl() = Action {
    implicit request =>
      urlForm.bindFromRequest.fold(
      errors => BadRequest,
      {
        case (url) =>
          PingActor.coordinator ! AddPing(url)
          Redirect(routes.Application.index())
      }
      )
  }

  def pingStream = Action {
    AsyncResult {
      implicit val timeout = Timeout(5.seconds)
      (PingActor.coordinator ? (Listen())).mapTo[Enumerator[String]].asPromise.map { chunks =>
        Ok.stream(chunks &> Comet(callback = "parent.message"))
      }
    }
  }
}