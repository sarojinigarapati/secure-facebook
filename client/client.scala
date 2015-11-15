import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import scala.concurrent.duration._
import spray.routing.SimpleRoutingApp
import spray.client.pipelining._
import spray.http._
import scala.util.{Success, Failure}

object project4 {
	def main(args: Array[String]){

		implicit val system = ActorSystem("ClientSystem")

		import system.dispatcher
		val pipeline = sendReceive
		val responseFuture = pipeline(Get("http://localhost:8080/hello"))
		responseFuture onComplete {
			case Success(str: HttpResponse) =>
				println(str)
			case Failure(error) =>
				println(error+"Couldn't get elevation")
				// shutdown()
		}

		// def shutdown(): Unit = {
		// 	IO(Http).ask(Http.CloseAll)(1.second).await
		// 	system.shutdown()
		// }
		
	}	
}
