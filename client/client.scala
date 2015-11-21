import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import scala.util.Random
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
							println(str.message())
							// self ! StartSimulation()
						case Failure(error) =>
							println(error+"something wrong")
					}
					

				case StartSimulation() =>
					println("Server is initialized! We can start the simulation!")
					for(i <- 0 until numOfUsers){
						var myID: String = i.toString
						val actor = context.actorOf(Props(new FacebookAPI(system, i, numOfUsers)),name=myID)
						actor ! "DoActivity"
					}
			}
		}

		class FacebookAPI(system: ActorSystem, myID: Int, numOfUsers: Int) extends Actor{
			import system.dispatcher
			val pipeline = sendReceive

			def receive = {

				case "DoActivity" =>

					import system.dispatcher
					if(myID % 2 == 0 ){
						system.scheduler.schedule(0 milliseconds,10 milliseconds,self,"Post")
					} else {
						system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Post")
					}
					
					// if(System.currentTimeMillis - start < 300000){ // 5 Minutes
					// 	self ! "DoActivity"
					// } else {
					// 	context.system.shutdown()
					// }

				case "Post" =>
					var text: String = Random.alphanumeric.take(5).mkString
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/Post?userID="+myID+"&text="+text))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println(str.message())
						case Failure(error) =>
							println(error+"something wrong")
					}

				// case AddFriend(userID:Int) =>
				// 	pipeline(Post("http://localhost:8080/facebook/addfriend?userID="+userID))


			}
		}

		if(1>args.size){
			println("Please enter number of facebook users to start simulation!")
		} else {
			val start: Long = System.currentTimeMillis
			implicit val system = ActorSystem("ClientSystem")
			val clientMaster =system.actorOf(Props(new ClientMaster((args(0).toInt),system)),name="clientMaster")
			clientMaster ! Inilialize()
		}
		
	}	
}
