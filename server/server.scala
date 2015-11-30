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

		case class Init(numOfUsers: Int)
		case class AddProfile(userID: Int, profile: Profile)
		case class GetProfile(userID: Int)
		case class Post(userID: Int, text: String)
		case class AddFriend(userID: Int, totalUsers: Int)
		case class AddAlbum(userID: Int, albumID: Int, album: Album) 
		case class UnFriend(userID: Int, firendID: Int)

		case class dummy(first: String, second: String)
		case class BigDummy(userID: String, d: dummy)
		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, profile: Profile)
		case class Picture(id: Int, from: Int, text: String, likes: Array[String])
		case class Album(id: Int, from: Int, pictures: Array[Picture], likes: Array[String])
		// case class AlbumWrapper(userID: Int, albumID: Int, album: Album)

		// class Album(input_id: String, input_from: String, input_taggers: ArrayBuffer[String]){
		//    	// Fields
		//    	var id: String = input_id
		//    	var from: String = input_from
		//    	// Edges
		//    	var likes: ArrayBuffer[String] = input_taggers
		// }

		object MyJsonProtocol extends DefaultJsonProtocol {
			implicit val ObjFormat = jsonFormat7(Profile)
			implicit val SignUpFormat = jsonFormat2(SignUp)
			implicit val PictureFormat = jsonFormat4(Picture)
			implicit val AlbumFormat = jsonFormat4(Album)

			implicit val dummyFormat = jsonFormat2(dummy)
			implicit val BigDummyFormat = jsonFormat2(BigDummy)

			// implicit object AlbumJsonFormat extends RootJsonFormat[Album] {
			   
			//     def write(album: Album) = JsObject(
			//       "id" -> JsString(album.id),
			//       "from" -> JsString(album.from),
			//       "likes" -> JsArray(album.likes.map(_.toJson).toVector))

			//     def read(value: JsValue) = {
			//       value.asJsObject.getFields("id", "from", "likes") match {
			//         case Seq(JsString(id), JsString(from), JsArray(likes)) =>
			//          	new Album(id, from, likes.map(_.convertTo[String]).to[ArrayBuffer])
			//        	case Seq(JsString(message), JsString(from), JsArray(likes)) =>
			//         	new Album(null, from, likes.map(_.convertTo[String]).to[ArrayBuffer])
			//        	case _ =>
			//         	throw new DeserializationException("Invalid Album")
			//      	}
			//    }
			// }

			// implicit val AlbumWrapperFormat = jsonFormat3(AlbumWrapper)

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
				path("facebook"/"getAlbum") {
					parameters("userID".as[Int], "albumID".as[Int]) { (userID, albumID) =>
						// try catch for java.lang.IndexOutOfBoundsException
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
				path("facebook"/"addProfile"){
					entity(as[SignUp]) { signUp =>
						println("Adding a profile!")
						server ! AddProfile(signUp.id, signUp.profile)
						complete {
							"User "+signUp.id+" added"
						}
					}
				}
			}~
			post {
				path("facebook"/"addAlbum"){
					entity(as[Album]) { album =>
						// println("Adding an album!")
						server ! AddAlbum(album.from.toInt, album.id.toInt, album)
						complete {
							"User "+album.from.toInt+" added an album"
						}
					}
				}
			}~
			// post {
			// 	path("facebook"/"addAlbum"){
			// 		entity(as[AlbumWrapper]) { albumWrapper =>
			// 			println("Adding an album!")
			// 			server ! AddAlbum(albumWrapper.userID, albumWrapper.albumID, albumWrapper.album)
			// 			complete {
			// 				"User "+albumWrapper.userID+" added an album"
			// 			}
			// 		}
			// 	}
			// }~
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
