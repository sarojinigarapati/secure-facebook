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
		var friendLists = new ArrayBuffer[ListBuffer[Int]]()
		var profiles = new HashMap[Int, SignUp]()  // store list of class objects
		var publicKeys = new HashMap[Int, PublicKey]()
		var accessProfiles = new HashMap[Int, HashMap[Int, String]]() 
		var posts = new HashMap[Int, HashMap[Int, fPost]]()
		var pics = new HashMap[Int, HashMap[Int, Pic]]()
		var albums = new HashMap[Int, HashMap[Int, Album]]()
		var pages = new HashMap[Int, HashMap[Int, Page]]()
		var frdRequests = new HashMap[Int, ListBuffer[Int]]()

		case class Init(numOfUsers: Int)
		case class AddProfile(userID: Int, profile: SignUp)
		case class GetProfile(userID: Int)
		case class AddPost(userID: Int, postID: Int, post: fPost)
		case class AddPicture(userID: Int, picID: Int, pic: Pic)
		case class AddAlbum(userID: Int, albumID: Int, album: Album) 
		case class AddPage(userID: Int, pageID: Int, page: Page)

		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, pkey: Array[Byte], profile: Array[Byte])
		case class fPost(id: Int, from: Int, data: Array[Byte], acl: Array[Int], aesKeys: Array[Array[Byte]])
		case class PostFrdList(frdList: Array[Int], frdPubKeys: Array[Array[Byte]])
		case class Pic(id: Int, from: Int, data: Array[Byte], picAcl: Array[Int], picAesKeys: Array[Array[Byte]])
		case class PicFrdList(frdList: Array[Int], frdPubKeys: Array[Array[Byte]])
		case class Album(id: Int, from: Int, data: Array[Array[Byte]], acl: Array[Int], aesKeys: Array[Array[Byte]])
		case class FrdList(frdList: Array[Int], frdPubKeys: Array[Array[Byte]])
		
		case class Picture(id: Int, from: Int, text: String, likes: Array[String])
		case class Page(id: Int, from: Int, name: String)
		case class AccessFriend(userID: Int, frdID: Int, bytes: Array[Byte])
		case class SendProfile(profileAesKey: String, profileBytes: Array[Byte])
		case class SendPic(picAesKey: Array[Byte], data: Array[Byte])
		case class SendPost(aesKey: Array[Byte], data: Array[Byte])
		case class SendAlbum(aesKey: Array[Byte], data: Array[Array[Byte]])
		// case class AccessFriend(userID: Int, frdID: Int, bytes: String)

		object MyJsonProtocol extends DefaultJsonProtocol {
			implicit val ProfileFormat = jsonFormat7(Profile)
			implicit val SignUpFormat = jsonFormat3(SignUp)
			implicit val PictureFormat = jsonFormat4(Picture)
			implicit val PageFormat = jsonFormat3(Page)
			implicit val AccessFriendFormat = jsonFormat3(AccessFriend)
			implicit val PostFormat = jsonFormat5(fPost)
			implicit val PostFrdListFormat = jsonFormat2(PostFrdList)
			implicit val PicFormat = jsonFormat5(Pic)
			implicit val PicFrdListFormat = jsonFormat2(PicFrdList)
			implicit val AlbumFormat = jsonFormat5(Album)
			implicit val FrdListFormat = jsonFormat2(FrdList)
			implicit val SendProfileFormat = jsonFormat2(SendProfile)
			implicit val SendPicFormat = jsonFormat2(SendPic)
			implicit val SendPostFormat = jsonFormat2(SendPost)
			implicit val SendAlbumFormat = jsonFormat2(SendAlbum)
		}

		println("project4 - Facebook Simulator")
		class Server(num: Int) extends Actor{

			var Users: Int = _
			
			def receive = {
				case Init(numOfUsers: Int) =>
					println("Initialise profiles for "+numOfUsers+" users!")
					for(i <- 0 to numOfUsers-1) friendLists += new ListBuffer[Int]
					// for(i <- 0 to numOfUsers-1) posts += new Queue[String]
					for(i <- 0 to numOfUsers-1) posts(i) = new HashMap[Int,fPost]
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

				case AddPost(userID: Int, postID: Int, post: fPost) =>
					posts(userID)(postID) = post

				case AddPicture(userID: Int, picID: Int, pic: Pic) =>
					pics(userID)(picID) = pic

				case AddAlbum(userID: Int, albumID: Int, album: Album) =>
					albums(userID)(albumID) = album

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
						var frdList: Array[Int] = friendLists(userID).toArray
						var frdPubKeys: ArrayBuffer[Array[Byte]] = ArrayBuffer[Array[Byte]]()
						for(i <- 0 until frdList.length){
							frdPubKeys += profiles(frdList(i)).pkey 
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
				path("facebook"/"getPicture") {
					parameters("userID".as[Int], "frdID".as[Int], "picID".as[Int]) { (userID, frdID, picID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						if(pics(frdID).contains(picID)){
							if(pics(frdID)(picID).picAcl.contains(userID)){
								println("Sending picture "+picID+" of user "+frdID+" to user "+userID)
								// val bytes = Base64.decodeBase64(accessProfiles(getID)(userID))
								var index: Int = pics(frdID)(picID).picAcl.indexOf(userID)
								val res = SendPic(pics(frdID)(picID).picAesKeys(index), pics(frdID)(picID).data )
								complete {
									res
								}
							} else {
								println("User "+userID+" dont have access to picture "+picID+" of user "+frdID)
								complete {
									"PermissionDenied"
								}
							}
						} else {
							complete {
								"PictureNotFound"
							}
						}
						
					}
				}
			}~
			post {
				path("facebook"/"initPost") {
					parameters("userID".as[Int]) { (userID) =>
						var frdList: Array[Int] = friendLists(userID).toArray
						var frdPubKeys: ArrayBuffer[Array[Byte]] = ArrayBuffer[Array[Byte]]()
						for(i <- 0 until frdList.length){
							frdPubKeys += profiles(frdList(i)).pkey 
						}
						val res = PostFrdList(frdList,frdPubKeys.toArray)
						// println(frdList.toJson)
						// println(frdPubKeys.toArray.toJson)
						complete {
							res
						}
					}
				}
			}~
			post {
				path("facebook"/"addPost") {
					entity(as[fPost]) { post =>
						println("Adding a post for user "+post.from)
						server ! AddPost(post.from, post.id, post)
						complete {
							"ok"
						}
					}
				}
			}~
			post {
				path("facebook"/"getPost") {
					parameters("userID".as[Int], "frdID".as[Int], "postID".as[Int]) { (userID, frdID, postID) =>
						// try catch for java.lang.IndexOutOfBoundsException
						if(posts(frdID).contains(postID)){
							if(posts(frdID)(postID).acl.contains(userID)){
								println("Sending post "+postID+" of user "+frdID+" to user "+userID)
								// val bytes = Base64.decodeBase64(accessProfiles(getID)(userID))
								var index: Int = posts(frdID)(postID).acl.indexOf(userID)
								val res = SendPost(posts(frdID)(postID).aesKeys(index), posts(frdID)(postID).data )
								complete {
									res
								}
							} else {
								println("User "+userID+" dont have access to post "+postID+" of user "+frdID)
								complete {
									"PermissionDenied"
								}
							}
						} else {
							complete {
								"PostNotFound"
							}
						}
						
					}
				}
			}~
			post {
				path("facebook"/"initAlbum") {
					parameters("userID".as[Int]) { (userID) =>
						var frdList: Array[Int] = friendLists(userID).toArray
						var frdPubKeys: ArrayBuffer[Array[Byte]] = ArrayBuffer[Array[Byte]]()
						for(i <- 0 until frdList.length) frdPubKeys += profiles(frdList(i)).pkey 
						complete {
							FrdList(frdList,frdPubKeys.toArray)
						}
					}
				}
			}~
			post {
				path("facebook"/"addAlbum") {
					entity(as[Album]) { album =>
						println("Adding an album for user "+album.from)
						server ! AddAlbum(album.from, album.id, album)
						complete {
							"ok"
						}
					}
				}
			}~
			post {
				path("facebook"/"getalbum") {
					parameters("userID".as[Int], "frdID".as[Int], "albumID".as[Int]) { (userID, frdID, albumID) =>
						if(albums(frdID).contains(albumID)){
							if(albums(frdID)(albumID).acl.contains(userID)){
								println("Sending album "+albumID+" of user "+frdID+" to user "+userID)
								var index: Int = albums(frdID)(albumID).acl.indexOf(userID)
								complete {
									SendAlbum(albums(frdID)(albumID).aesKeys(index), albums(frdID)(albumID).data )
								}
							} else {
								println("User "+userID+" dont have access to album "+albumID+" of user "+frdID)
								complete {
									"PermissionDenied"
								}
							}
						} else {
							complete {
								"AlbumNotFound"
							}
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