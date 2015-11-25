import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.RoundRobinRouter
import scala.util.Random
import scala.concurrent.duration._
import scala.collection.mutable._
import spray.routing.SimpleRoutingApp
import spray.http._

object project4 extends App with SimpleRoutingApp{
	override def main(args: Array[String]){

		val AvgNumOfFrds: Int = 5
		var posts = new ListBuffer[Queue[String]]()
		var friendLists = new ListBuffer[ListBuffer[Int]]()
		var profiles = new ListBuffer[Profile]()  // store list of class objects

		case class Init(numOfUsers: Int)
		case class GetProfile(userID: Int)
		case class Post(userID: Int, text: String)
		case class AddFriend(userID: Int)
		case class UnFriend(userID: Int, firendID: Int)

		println("project4 - Facebook Simulator")
		class Server(num: Int) extends Actor{

			private var numOfUsers: Int = _
			
			def receive = {
				case Init(numOfUsers: Int) =>
					println("Initialise profiles for "+numOfUsers+" users!")
					for(i <- 0 to numOfUsers-1) friendLists += new ListBuffer[Int]
					for(i <- 0 to numOfUsers-1) posts += new Queue[String]
					for(i <- 0 to numOfUsers-1){ // Create random profiles
						var id: Int = i
						var first_name: String = Random.alphanumeric.take(5).mkString
						var last_name: String = Random.alphanumeric.take(5).mkString
						var age: Int = Random.nextInt(82) + 18
						var email: String = Random.alphanumeric.take(10).mkString
						var gender: String = ""
						if(Random.nextInt(1) == 0){
							gender = "Male"
						} else {
							gender = "Female"
						}
						var relation_status = ""
						if(Random.nextInt(1) == 0){
							relation_status = "Single"
						} else {
							relation_status = "Married"
						}
						profiles += new Profile(id, first_name, last_name, age, email, gender, relation_status)
					}
					// Populate the friends list
					for(i <- 0 to numOfUsers-1){
						for(j <- 0 to AvgNumOfFrds){
							var frd: Int = Random.nextInt(numOfUsers)
							if(frd != i && !friendLists(i).contains(frd)){
								friendLists(i) += frd
							}
						}
					}
					this.numOfUsers = numOfUsers

				case GetProfile(userID: Int) =>

				case Post(userID: Int, text: String) =>
					posts(userID) += text

				case AddFriend(userID: Int) =>
					var frd: Int = Random.nextInt(numOfUsers)
					while(frd == userID || friendLists(userID).contains(frd)){
						frd = Random.nextInt(numOfUsers)
					}
					friendLists(userID) += frd

				case UnFriend(userID: Int, firendID: Int) =>




			}
		}

		class Profile(id: Int, first_name: String, last_name: String, age: Int, email: String, gender: String, relation_status: String) {
			
		}

		implicit val system = ActorSystem("ServerSystem")
		val numOfServerActors: Int = Runtime.getRuntime().availableProcessors()*100
		val server = system.actorOf(Props(new Server(5)).withRouter(RoundRobinRouter(numOfServerActors)), name = "server")

		startServer("localhost", port = 8080) {
			get {
				path("hello") {
					complete {
						"Hello am there"
					}
				}
			} ~
			get {
				path("facebook"/"getprofile") {
					parameters("userID".as[Int]) { (userID) =>
						server ! GetProfile(userID)
						complete {
							"Getting profile for user "+userID
						}
					}
				}
			}~
			post {
				path("facebook"/"start") {
					parameters("numOfUsers".as[Int]) { (numOfUsers) =>
						server ! Init(numOfUsers)
						complete {
							"Initialization is done"
						}
					}
				}
			}~
			post {
				path("facebook"/"post") {
					parameters("userID".as[Int], "text".as[String]) { (userID, text) =>
						server ! Post(userID, text)
						complete {
							"User "+userID+" posted "+text
						}
					}
				}
			}~
			post {
				path("facebook"/"addfriend") {
					parameters("userID".as[Int]) { (userID) =>
						server ! AddFriend(userID)
						complete {
							"Added a friend to user "+userID
						}
					}
				}
			}~
			post {
				path("facebook"/"unfriend") {
					parameters("userID".as[Int], "firendID".as[Int]) { (userID, firendID) =>
						server ! UnFriend(userID, firendID)
						complete {
							"User "+ userID+" removed "+firendID+" from his/her friend list"
						}
					}
				}
			}
		}

	}	
}
