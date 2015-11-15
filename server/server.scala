import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.RoundRobinRouter
import scala.concurrent.duration._
import spray.routing.SimpleRoutingApp
import spray.http._

object project4 extends App with SimpleRoutingApp{
	override def main(args: Array[String]){

		val numOfServerActors: Int = Runtime.getRuntime().availableProcessors()*100

		case class Hello()

		println("project4 - Facebook Simulator")
		class Server(num: Int) extends Actor{

			
			def receive = {
				case Hello() =>
					

			}
		}

		implicit val system = ActorSystem("ServerSystem")
		val server = system.actorOf(Props(new Server(5)).withRouter(RoundRobinRouter(numOfServerActors)), name = "server")

		startServer("localhost", port = 8080) {
			get {
				pathSingleSlash {
					redirect("/hello", StatusCodes.Found)
				} ~
				path("hello") {
					complete {
						"Hello am there"
					}
				}
			} ~
			(post | parameter('method ! "post")) {
				path("stop") {
					complete {
						system.scheduler.scheduleOnce(1.second)(system.shutdown())(system.dispatcher)
						"Shutting down in 1 second..."
					}
				}
			}
		}

	}	
}
