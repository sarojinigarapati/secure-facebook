import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.routing.RoundRobinRouter
import scala.util.Random
import scala.concurrent.duration._
import scala.collection.mutable._
import spray.routing.SimpleRoutingApp
import spray.http.MediaTypes
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._
import DefaultJsonProtocol._ 

object project4 extends App with SimpleRoutingApp{
	override def main(args: Array[String]){

		val AvgNumOfFrds: Int = 5
		var posts = new ArrayBuffer[Queue[String]]()
		var friendLists = new ArrayBuffer[ListBuffer[Int]]()
		var profiles = new HashMap[Int, Profile]()  // store list of class objects
		var albums = new HashMap[Int, HashMap[Int, Album]]()
		var pages = new HashMap[Int, HashMap[Int, Page]]()

		case class Init(numOfUsers: Int)
		case class AddProfile(userID: Int, profile: Profile)
		case class GetProfile(userID: Int)
		case class Post(userID: Int, text: String)
		case class AddFriend(userID: Int, totalUsers: Int)
		case class AddAlbum(userID: Int, albumID: Int, album: Album) 
		case class AddPage(userID: Int, pageID: Int, page: Page)

		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, profile: Profile)
		case class Picture(id: Int, from: Int, text: String, likes: Array[String])
		case class Album(id: Int, from: Int, pictures: Array[Picture], likes: Array[String])
		case class Page(id: Int, from: Int, name: String)
		// case class AlbumWrapper(userID: Int, albumID: Int, album: Album)

		object MyJsonProtocol extends DefaultJsonProtocol {
			implicit val ObjFormat = jsonFormat7(Profile)
			implicit val SignUpFormat = jsonFormat2(SignUp)
			implicit val PictureFormat = jsonFormat4(Picture)
			implicit val AlbumFormat = jsonFormat4(Album)
			implicit val PageFormat = jsonFormat3(Page)
		}

		println("project4 - Facebook Simulator")
		class Server(num: Int) extends Actor{

			var Users: Int = _
			
			def receive = {
				case Init(numOfUsers: Int) =>
					println("Initialise profiles for "+numOfUsers+" users!")
					for(i <- 0 to numOfUsers-1) friendLists += new ListBuffer[Int]
					for(i <- 0 to numOfUsers-1) posts += new Queue[String]
					for(i <- 0 to numOfUsers-1) albums(i) = new HashMap[Int,Album]
					for(i <- 0 to numOfUsers-1){ // Create random profiles
						profiles(i) = Profile("NA", "NA", "NA", "NA", "NA", "NA", "NA")
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

				case AddAlbum(userID: Int, albumID: Int, album: Album) =>
					albums(userID)(albumID) = album
					// println(albums(userID)(albumID).toString)
				case AddPage(userID: Int, pageID: Int, page: Page) =>
					pages(userID)(pageID) = page

				case "Shutdown" =>
					// context.system.shutdown()
			}
		}

		implicit val system = ActorSystem("ServerSystem")
		val numOfServerActors: Int = Runtime.getRuntime().availableProcessors()*100
		val server = system.actorOf(Props(new Server(5)).withRouter(RoundRobinRouter(numOfServerActors)), name = "server")
		import MyJsonProtocol._
		startServer("localhost", port = 8080) {
			get {
				path("hello") {
					complete {
						"Hello am there!!!\n"
					}
				}
			} ~
			post {
				path("facebook"/"shutdown") {
					server ! "Shutdown"
					complete {
						"OK"
					}
				}
			}~
			post {
				path("facebook"/"AddProfile") {
					parameters("userID".as[Int], "profile".as[String]) { (userID, profile) =>
						println("Adding a profile for user "+userID)
						// println(profile)
						// server ! AddProfile(userID, profile)
						complete {
							profile
						}
					}
				}
			}~
			post {
				path("facebook"/"addProfile"){
					entity(as[Array[Byte]]) { bytes =>
						println("ok")
						// println("Adding a profile for user "+signUp.id)
						// server ! AddProfile(signUp.id, signUp.profile)
						complete {
							bytes
						}
					}
				}
			}~
			post {
				path("facebook"/"getProfile") {
					parameters("userID".as[Int]) { (userID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						println("Sending profile to user "+userID)
						complete {
							profiles(userID)
						}
					}
				}
			}~
			post {
				path("facebook"/"getPage") {
					parameters("userID".as[Int], "pageID".as[Int]) { (userID, pageID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						println("Sending a page of user "+userID)
						var res: Page = Page(0,0,"Invalid Page id!!")
						if((pages(userID).contains(pageID)) == true){
							res = pages(userID)(pageID)
						}
						complete {
							res
						}
					}
				}
			}~
			post {
				path("facebook"/"getPosts") {
					parameters("userID".as[Int]) { (userID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						println("Sending posts to user "+userID)
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
						println("Sending friend list to user "+userID)
						complete {
							friendLists(userID).toString()
						}
					}
				}
			}~
			post {
				path("facebook"/"getAlbum") {
					parameters("userID".as[Int], "albumID".as[Int]) { (userID, albumID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						println("Sending an album to user "+userID)
						var dummyPic: Picture = Picture(0,0,"dummyPicture",Array("0"))
						var res: Album = Album(0,0,Array(dummyPic),Array("0"))
						if((albums(userID).contains(albumID)) == true){
							res = albums(userID)(albumID)
						}
						complete {
							res
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
				path("facebook"/"addPage"){
					entity(as[Page]) { page =>
						println("Adding a page for user "+page.from)
						server ! AddPage(page.from, page.id, page)
						complete {
							"Page added for user "+page.from
						}
					}
				}
			}~
			post {
				path("facebook"/"addAlbum"){
					entity(as[Album]) { album =>
						println("Adding an album for user "+album.from.toInt)
						server ! AddAlbum(album.from.toInt, album.id.toInt, album)
						complete {
							"User "+album.from.toInt+" added an album"
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
			}
		}

	}	
}
