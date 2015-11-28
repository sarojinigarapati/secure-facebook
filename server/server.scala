import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.RoundRobinRouter
import scala.util.Random
import scala.concurrent.duration._
// import scala.collection.immutable.Queue
import scala.collection.mutable._
import spray.routing.SimpleRoutingApp
import spray.http.MediaTypes
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._
import spray.json._
import DefaultJsonProtocol._ 



object project4 extends App with SimpleRoutingApp{
	override def main(args: Array[String]){

		val AvgNumOfFrds: Int = 5
		var posts = new ListBuffer[Queue[String]]()
		var friendLists = new ListBuffer[ListBuffer[Int]]()
		var profiles = new ListBuffer[Profile]()  // store list of class objects
		var albums = new ListBuffer[ListBuffer[ListBuffer[String]]]

		case class Init(numOfUsers: Int)
		case class AddProfile(userID: Int, profile: Profile)
		case class GetProfile(userID: Int)
		case class Post(userID: Int, text: String)
		case class AddFriend(userID: Int, totalUsers: Int)
		case class UnFriend(userID: Int, firendID: Int)
		case class dummy(first: String, second: String)
		case class BigDummy(userID: String, d: dummy)
		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, profile: Profile)

		object MyJsonProtocol extends DefaultJsonProtocol {
			implicit val ObjFormat = jsonFormat7(Profile)
			implicit val SignUpFormat = jsonFormat2(SignUp)
			implicit val dummyFormat = jsonFormat2(dummy)
			implicit val BigDummyFormat = jsonFormat2(BigDummy)
		}

		import MyJsonProtocol._

		println("project4 - Facebook Simulator")
		class Server(num: Int) extends Actor{

			var Users: Int = _
			
			def receive = {
				case Init(numOfUsers: Int) =>
					println("Initialise profiles for "+numOfUsers+" users!")
					for(i <- 0 to numOfUsers-1) friendLists += new ListBuffer[Int]
					for(i <- 0 to numOfUsers-1) posts += new Queue[String]
					for(i <- 0 to numOfUsers-1){ // Create random profiles
						profiles += Profile("0", "NA", "NA", "18", "NA", "NA", "NA")
					}
					Users = numOfUsers
					println("Initialised for "+Users+" user!!")

				case AddProfile(userID: Int, profile: Profile) =>
					profiles(userID) = profile
					friendLists(userID) += userID

				case Post(userID: Int, text: String) =>
					posts(userID) += text

				case AddFriend(userID: Int, totalUsers: Int) =>
					// println("Adding friend to user "+userID)
					var frd: Int = Random.nextInt(totalUsers)
					var count: Int = 0
					while((5>count) && friendLists(userID).contains(frd)){
						frd = Random.nextInt(totalUsers)
						count = count + 1
					}
					if(count < 5){
						friendLists(userID) += frd
					}

				case UnFriend(userID: Int, firendID: Int) =>

			}
		}

		implicit val system = ActorSystem("ServerSystem")
		val numOfServerActors: Int = Runtime.getRuntime().availableProcessors()*100
		val server = system.actorOf(Props(new Server(5)).withRouter(RoundRobinRouter(numOfServerActors)), name = "server")

		startServer("localhost", port = 8080) {
			get {
				path("hello") {
					complete {
						"Hello am there!!!\n"
					}
				}
			} ~
			post {
				path("facebook"/"getProfile") {
					parameters("userID".as[Int]) { (userID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						complete {
							profiles(userID)
						}
					}
				}
			}~
			post {
				path("facebook"/"getPosts") {
					parameters("userID".as[Int]) { (userID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						complete {
							posts(userID).toString()
						}
					}
				}
			}~
			post {
				path("facebook"/"getFriendList") {
					parameters("userID".as[Int]) { (userID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						complete {
							friendLists(userID).toString()
						}
					}
				}
			}~
			post {
				path("dummy"){
						entity(as[BigDummy]) { d =>
							complete {
								d
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
				path("facebook"/"addProfile"){
					entity(as[SignUp]) { signUp =>
						server ! AddProfile(signUp.id, signUp.profile)
						complete {
							"User "+signUp.id+" added"
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
				path("facebook"/"addFriend") {
					parameters("userID".as[Int], "totalUsers".as[Int]) { (userID, totalUsers) =>
						server ! AddFriend(userID, totalUsers)
						complete {
							"Added a friend to user "+userID
						}
					}
				}
			}~
			post {
				path("facebook"/"unFriend") {
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
