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

		case class Inilialize()
		case class StartSimulation()
		case class Post(userID: Int)

		class ClientMaster(numOfUsers: Int, system: ActorSystem) extends Actor{
			import system.dispatcher
			val pipeline = sendReceive
			
			def receive = {
				case Inilialize() =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/start?numOfUsers="+numOfUsers))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println(str)
							self ! StartSimulation()
						case Failure(error) =>
							println(error+"something wrong")
					}

				case StartSimulation() =>
					println("Server is initialized! We can start the simulation!")
			}
		}

		class FacebookAPI(system: ActorSystem) extends Actor{
			import system.dispatcher
			val pipeline = sendReceive

			def receive = {
				case Post(userID: Int) =>
					var text: String = Random.alphanumeric.take(5).mkString
					pipeline(Post("http://localhost:8080/facebook/Post?userID="+userID+"text"+text))

				case AddFriend(userID:Int) =>
					pipeline(Post("http://localhost:8080/facebook/addfriend?userID="+userID))


			}
		}

		if(1>args.size){
			println("Please enter number of facebook users to start simulation!")
		} else {
			implicit val system = ActorSystem("ClientSystem")
			val clientMaster =system.actorOf(Props(new ClientMaster((args(0).toInt),system)),name="clientMaster")
			clientMaster ! Inilialize()
		}
		
	}	
}
