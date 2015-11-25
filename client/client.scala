import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Cancellable;
import scala.util.Random
import scala.concurrent.duration._
import spray.client.pipelining._
import spray.http._
import scala.util.{Success, Failure}

object project4 {
	def main(args: Array[String]){

		val start: Long = System.currentTimeMillis
		case class Inilialize()
		case class StartSimulation()
		case class Stat(userPosts: Int)

		class ClientMaster(numOfUsers: Int, system: ActorSystem) extends Actor{

			var count: Int = _
			var totalPosts: Int = _

			import system.dispatcher
			val pipeline = sendReceive
			
			def receive = {
				case Inilialize() =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/start?numOfUsers="+numOfUsers))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println(str.entity.asString)
							self ! StartSimulation()
						case Failure(error) =>
							println(error+"something wrong")
					}
					

				case StartSimulation() =>
					println("Server is initialized! We can start the simulation!")
					for(i <- 0 until numOfUsers){
						var myID: String = i.toString
						val actor = context.actorOf(Props(new FacebookAPI(system, i, numOfUsers)),name=myID)
						actor ! "DoActivity"
						actor ! "Stop"
					}

				case Stat(userPosts: Int) =>
					totalPosts = totalPosts + userPosts
					count = count + 1
					if(count == numOfUsers){
						println("Total number of posts = "+totalPosts)
						system.scheduler.scheduleOnce(1000 milliseconds, self, "ShutDown")
					}

				case "ShutDown" =>
					context.system.shutdown()
			}
		}

		class FacebookAPI(system: ActorSystem, myID: Int, numOfUsers: Int) 
			extends Actor{

			var numOfPosts: Int =_
			var cancelStop: Cancellable =_
			var cancelPosts1: Cancellable =_
			var cancelPosts2: Cancellable =_

			import system.dispatcher
			val pipeline = sendReceive

			def receive = {

				case "DoActivity" =>

					// import system.dispatcher
					cancelStop = system.scheduler.schedule(0 milliseconds,10 milliseconds,self,"Stop")
					if(myID % 2 == 0 ){
						cancelPosts1 = system.scheduler.schedule(0 milliseconds,10 milliseconds,self,"Post")
					} else {
						cancelPosts2 = system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Post")
					}
					
				case "Stop" =>
					if(System.currentTimeMillis - start > 10000){ // in milliseconds
						cancelStop.cancel()
						if(myID % 2 == 0 ){
							cancelPosts1.cancel()
						} else {
							cancelPosts2.cancel() 
						}
						context.parent ! Stat(numOfPosts)
					}

				case "Post" =>
					var text: String = Random.alphanumeric.take(10).mkString
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/post?userID="+myID+"&text="+text))
					// println("User "+myID+" sending post "+numOfPosts+1)
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println("User "+myID+" successfully posted "+numOfPosts+1)
							numOfPosts = numOfPosts + 1
							// println(str.entity.asString)
							// println("User "+myID+" posted "+text)
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
			implicit val system = ActorSystem("ClientSystem")
			val clientMaster =system.actorOf(Props(new ClientMaster((args(0).toInt),system)),name="clientMaster")
			clientMaster ! Inilialize()
		}
		
	}	
}
