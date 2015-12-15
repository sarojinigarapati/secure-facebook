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
import java.security._
import java.security.spec._
import javax.crypto._
import org.apache.commons.codec.binary.Base64

object project4 extends App with SimpleRoutingApp{
	override def main(args: Array[String]){

		val AvgNumOfFrds: Int = 5
		var posts = new ArrayBuffer[Queue[String]]()
		var friendLists = new ArrayBuffer[ListBuffer[Int]]()
		var profiles = new HashMap[Int, SignUp]()  // store list of class objects
		var publicKeys = new HashMap[Int, PublicKey]()
		var accessProfiles = new HashMap[Int, HashMap[Int, String]]() 
		var pics = new HashMap[Int, HashMap[Int, Pic]]()
		var albums = new HashMap[Int, HashMap[Int, Album]]()
		var pages = new HashMap[Int, HashMap[Int, Page]]()
		var frdRequests = new HashMap[Int, ListBuffer[Int]]()
		// var frdRequests = new ArrayBuffer[ListBuffer[Int]]()

		case class Init(numOfUsers: Int)
		case class AddProfile(userID: Int, profile: SignUp)
		case class GetProfile(userID: Int)
		case class Post(userID: Int, text: String)
		case class AddPicture(userID: Int, picID: Int, pic: Pic)
		case class AddAlbum(userID: Int, albumID: Int, album: Album) 
		case class AddPage(userID: Int, pageID: Int, page: Page)

		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, pkey: Array[Byte], profile: Array[Byte])
		case class Pic(id: Int, from: Int, data: Array[Byte], picAcl: Array[Int], picAesKeys: Array[String])
		case class PicFrdList(frdList: Array[Int], frdPubKeys: Array[String])
		case class Picture(id: Int, from: Int, text: String, likes: Array[String])
		case class Album(id: Int, from: Int, pictures: Array[Picture], likes: Array[String])
		case class Page(id: Int, from: Int, name: String)
		case class AccessFriend(userID: Int, frdID: Int, bytes: Array[Byte])
		case class SendProfile(profileAesKey: String, profileBytes: Array[Byte])
		// case class AccessFriend(userID: Int, frdID: Int, bytes: String)

		object MyJsonProtocol extends DefaultJsonProtocol {
			implicit val ProfileFormat = jsonFormat7(Profile)
			implicit val SignUpFormat = jsonFormat3(SignUp)
			implicit val PictureFormat = jsonFormat4(Picture)
			implicit val AlbumFormat = jsonFormat4(Album)
			implicit val PageFormat = jsonFormat3(Page)
			implicit val AccessFriendFormat = jsonFormat3(AccessFriend)
			implicit val SendProfileFormat = jsonFormat2(SendProfile)
			implicit val PicFormat = jsonFormat5(Pic)
			implicit val PicFrdListFormat = jsonFormat2(PicFrdList)
		}

		println("project4 - Facebook Simulator")
		class Server(num: Int) extends Actor{

			var Users: Int = _
			
			def receive = {
				case Init(numOfUsers: Int) =>
					println("Initialise profiles for "+numOfUsers+" users!")
					for(i <- 0 to numOfUsers-1) friendLists += new ListBuffer[Int]
					for(i <- 0 to numOfUsers-1) posts += new Queue[String]
					for(i <- 0 to numOfUsers-1) pics(i) = new HashMap[Int,Pic]
					for(i <- 0 to numOfUsers-1) albums(i) = new HashMap[Int,Album]
					for(i <- 0 to numOfUsers-1) accessProfiles(i) = new HashMap[Int, String]
					for(i <- 0 to numOfUsers-1) frdRequests(i) = new ListBuffer[Int]
					for(i <- 0 to numOfUsers-1){ // Create random profiles
						profiles(i) = SignUp(0, Array[Byte](0), Array[Byte](0))
					}
					Users = numOfUsers
					println("Initialised for "+Users+" user!!") 

				case AddProfile(userID: Int, profile: SignUp) =>
					profiles(userID) = profile
					friendLists(userID) += userID

				case Post(userID: Int, text: String) =>
					posts(userID) += text

				case AddPicture(userID: Int, picID: Int, pic: Pic) =>
					pics(userID)(picID) = pic

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
			}~
			post {
				path("facebook"/"shutdown") {
					server ! "Shutdown"
					complete {
						"OK"
					}
				}
			}~
			post {
				path("facebook"/"initPicture") {
					parameters("userID".as[Int]) { (userID) =>
						println("Adding a picture for user "+userID)
						var frdList: Array[Int] = friendLists(userID).toArray
						var frdPubKeys: ArrayBuffer[String] = ArrayBuffer[String]()
						for(i <- 0 until frdList.length){
							var bytes: Array[Byte] = profiles(userID).pkey 
							frdPubKeys += Base64.encodeBase64String(bytes)
						}
						val res = PicFrdList(frdList,frdPubKeys.toArray)
						complete {
							res
						}
					}
				}
			}~
			post {
				path("facebook"/"addPicture") {
					entity(as[Pic]) { pic =>
						println("Adding a picture for user "+pic.from)
						server ! AddPicture(pic.from, pic.id, pic)
						complete {
							"ok"
						}
					}
				}
			}~
			post {
				path("facebook"/"addProfile"){
					entity(as[SignUp]) { signUp =>
						println("Adding a profile for user "+signUp.id)
						server ! AddProfile(signUp.id, signUp)
						complete {
							signUp.profile
						}
					}
				}
			}~
			post {
				path("facebook"/"getProfile") {
					parameters("userID".as[Int], "getID".as[Int]) { (userID, getID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						if(friendLists(userID).contains(getID)){
							if(userID == getID){
								val res = SendProfile("NA", profiles(getID).profile )
								complete {
									res
								}
							} else {
								println("Sending profile of user "+getID+" to user "+userID)
								// val bytes = Base64.decodeBase64(accessProfiles(getID)(userID))
								val res = SendProfile(accessProfiles(getID)(userID), profiles(getID).profile )
								complete {
									res
								}
							}
						} else {
							println("User "+userID+" is not friend of user "+getID)
							complete {
								"PermissionDenied"
							}
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
							friendLists(userID).toArray.toJson.toString
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
				path("facebook"/"acceptFriendList") {
					parameters("userID".as[Int]) { (userID) =>
						val res = frdRequests(userID).toArray
						frdRequests(userID).clear()
						complete {
							res
						}
					}
				}
			}~
			post {
				path("facebook"/"accessFriend"){
					entity(as[AccessFriend]) { x =>
						accessProfiles(x.userID)(x.frdID) = Base64.encodeBase64String(x.bytes)
						// friendLists(x.frdID) += x.userID
						complete {
							"ok"
						}
					}
				}
			}~
			post {
				path("facebook"/"addFriend") {
					parameters("userID".as[Int], "frdID".as[Int]) { (userID, frdID) =>
						// server ! AddFriend(userID, totalUsers)
						if(friendLists(userID).contains(frdID)){
							complete {
								"AlreadyFriend"
							}
						} else {
							frdRequests(frdID) += userID
							// val res = publicKeys(frdID).getEncoded()
							complete {
								profiles(frdID).pkey
							}
						}
					}
				}
			}~
			post {
				path("facebook"/"acceptFriend") {
					parameters("userID".as[Int], "frdID".as[Int]) { (userID, frdID) =>
						// server ! AddFriend(userID, totalUsers)
						if(friendLists(userID).contains(frdID)){
							complete {
								"AlreadyFriend"
							}
						} else {
							friendLists(userID) += frdID
							friendLists(frdID) += userID
							complete {
								profiles(frdID).pkey
							}
						}
					}
				}
			}
		

		}
	}	
}