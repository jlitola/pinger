package actors

import akka.actor._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.util.duration._
import play.api.Logger
import collection._
import play.api.libs.iteratee.{Enumerator, PushEnumerator}

/**
 */

case class PingResult(url: String, status : Int, duration : Long)

case class AddPing(url : String)
case class Ping()
case class Quit(channel: PushEnumerator[String])
case class Listen()
case class Stop()

class PingActor( val targetUrl : String ) extends Actor {
  def receive = {
    case Ping() =>
      val start = System.currentTimeMillis();
      WS.url(targetUrl).get().map( {r =>
        val status = r.status
        val duration = System.currentTimeMillis()-start
        PingActor.coordinator ! PingResult(targetUrl, status, duration)
        Akka.system.scheduler.scheduleOnce(60 seconds, self, new Ping())
      })

    case Stop() =>
      context.stop(self)

  }
}

class PingCoordinator extends Actor {
  var urlMap = new mutable.HashMap[String,ActorRef]()
  var listeners = Seq.empty[PushEnumerator[String]]

  def receive = {
    case AddPing(url) =>
      val actor = PingActor.system.actorOf(Props(new PingActor(url)))
      if (!urlMap.contains(url)) {
        urlMap.put(url, actor)
        actor ! Ping()
        Logger.info("Added url "+url+" to "+self)
      }

    case message @ PingResult(url, status, duration) =>
      Logger.info("Ping at "+url+": status="+status+" took "+duration+"ms, notifying "+listeners.size+" clients")
      listeners.foreach(_.push("url: "+message.url+" took "+message.duration+"ms"))

    case Listen() =>
      lazy val channel: PushEnumerator[String] = Enumerator.imperative[String](
        onComplete = self ! Quit(channel)
      )
      listeners = listeners :+ channel
      Logger.info("Added listener "+channel+" has "+listeners.size+" listeners")
      sender ! channel

    case Quit(channel) =>
      listeners = listeners.filterNot(_ == channel)
      Logger.info("Removed listener "+channel+" has "+listeners.size+" listeners")
  }
}

object PingActor {
  lazy val system = Akka.system
  lazy val coordinator = system.actorOf(Props[PingCoordinator], name="coordinator")
}
