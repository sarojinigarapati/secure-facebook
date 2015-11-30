import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Cancellable;
import scala.util.Random
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import spray.client.pipelining._
import spray.http._
import scala.util.{Success, Failure}
import spray.json._
import spray.httpx.SprayJsonSupport._
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._
import spray.http.MediaTypes

object project4 {
	def main(args: Array[String]){

		val simulationTime: Int = 20000 // in milliseconds
		val start: Long = System.currentTimeMillis
		var numOfPages: Int = 0

		case class Stat(userPosts: Int)
		case class GetProfile(userID: Int)
		case class GetAlbum(userID: Int, albumID: Int)
		case class GetPosts(userID: Int)
		case class GetFriendList(userID: Int)

		case class dummy(first: String, second: String)
		case class BigDummy(userID: String, d: dummy)
		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, profile: Profile)
		case class Picture(id: Int, from: Int, text: String, likes: Array[String])
		case class Album(id: Int, from: Int, pictures: Array[Picture], likes: Array[String])
		case class Page(id: Int, from: Int, name: String)
		// case class AlbumWrapper(userID: Int, albumID: Int,  album: Album)

		// class Album(input_id: String, input_from: String, input_taggers: ArrayBuffer[String]) extends java.io.Serializable {
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
			implicit val PageFormat = jsonFormat3(Page)
			
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

		import MyJsonProtocol._
		
		class ClientMaster(numOfUsers: Int, system: ActorSystem) extends Actor{

			var count: Int = _
			var totalPosts: Int = _

			import system.dispatcher
			val pipeline = sendReceive
			
			def receive = {
				case "Start" =>
					// tell the server about the number of users in the system and 
					// initialise the required data structures
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/start?numOfUsers="+numOfUsers))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
							self ! "StartSimulation"
						case Failure(error) =>
							println(error+"something wrong")
					}
				
				case "StartSimulation" =>
					// Create an actore for each facebook user
					println("\n**********************************************")
					println("Server is initialized! Starting the simulation!")
					println("Users doing sign up")
					println("**********************************************\n")
					for(i <- 0 until numOfUsers){
						var myID: String = i.toString
						val actor = context.actorOf(Props(new FacebookAPI(system, i, numOfUsers)),name=myID)
						actor ! "SignUp"
						actor ! "Stop"
					}

				case Stat(userPosts: Int) =>
					totalPosts = totalPosts + userPosts
					count = count + 1
					if(count == numOfUsers){
						println("Total number of posts = "+totalPosts)
						system.scheduler.scheduleOnce(5000 milliseconds, self, "ShutDown")
					}

				case "ShutDown" =>
					context.system.shutdown()
			}
		}

		class FacebookAPI(system: ActorSystem, myID: Int, numOfUsers: Int) 
			extends Actor{

			var numOfPosts: Int =_
			var numOfAlbums: Int =_
			// var cancels = new ListBuffer[Cancellable]()
			var cancelPosts1: Cancellable =_
			var cancelPosts2: Cancellable =_
			var cancelAddFriend: Cancellable =_
			var cancelAddAlbum: Cancellable =_
			var cancelGetProfile: Cancellable =_
			var cancelGetAlbum: Cancellable =_

			import system.dispatcher
			val pipeline = sendReceive

			def receive = {

				case "SignUp" =>
					// Create the profie data
					var id: String = myID.toString
					var first_name: String = Random.alphanumeric.take(5).mkString
					var last_name: String = Random.alphanumeric.take(5).mkString
					var age: String = (Random.nextInt(82) + 18).toString
					var email: String = Random.alphanumeric.take(10).mkString+"@facebook.com"
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
					// val json = s"""{"id": "$id", "first_name": "$first_name","last_name": "$last_name", "age": "$age", "email": "$email", "gender": "$gender", "relation_status": "$relation_status"}"""
					val json = SignUp(myID, Profile(id, first_name, last_name, 
									age, email, gender, relation_status)).toJson.toString
					
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/addProfile",HttpEntity(MediaTypes.`application/json`, json)))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
							// self ! GetProfile(myID)
							self ! "DoActivity"
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "DoActivity" =>
					system.scheduler.scheduleOnce(simulationTime milliseconds, self, "StopActivity")
					// cancelStop = system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Stop")
					
					cancelAddFriend = system.scheduler.schedule(0 milliseconds,2000 milliseconds,self,"AddFriend")
					cancelAddAlbum = system.scheduler.schedule(0 milliseconds,4000 milliseconds,self,"AddAlbum")
					cancelGetAlbum = system.scheduler.schedule(30 milliseconds,2000 milliseconds,self,GetAlbum(myID, 0))
					cancelGetProfile = system.scheduler.schedule(60 milliseconds,8000 milliseconds,self,GetProfile(Random.nextInt(numOfUsers)))
					if(myID % 2 == 0 ){
						cancelPosts1 = system.scheduler.schedule(150 milliseconds,3000 milliseconds,self,"Post")
					} else {
						cancelPosts2 = system.scheduler.schedule(180 milliseconds,6000 milliseconds,self,"Post")
					}
					// cancels += system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Stop")
					// if(myID % 2 == 0 ){
					// 	cancels += system.scheduler.schedule(0 milliseconds,50 milliseconds,self,"Post")
					// } else {
					// 	cancels += system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Post")
					// }
					// // cancels += system.scheduler.schedule(0 milliseconds,1000 milliseconds,self,"AddFriend")
					
				case "StopActivity" =>
					if(System.currentTimeMillis - start > simulationTime){ // in milliseconds
						// println("Shutting down the system!!")
						// cancelStop.cancel()
						cancelAddFriend.cancel()
						cancelAddAlbum.cancel()
						cancelGetProfile.cancel()
						cancelGetAlbum.cancel()
						if(myID % 2 == 0 ){
							cancelPosts1.cancel()
						} else {
							cancelPosts2.cancel()
						}
						// self ! GetPosts(myID)
						// self ! GetFriendList(myID)
						context.parent ! Stat(numOfPosts)
					}

				case GetProfile(userID: Int) =>
					println("user "+myID+" requesting profile of "+userID)
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getProfile?userID="+userID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println("user "+myID+" received profile of "+userID+"\n"+
								str.entity.asString)
								// str.entity.asString.parseJson.convertTo[Profile].age)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GetPosts(userID: Int) =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getPosts?userID="+userID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println("user "+myID+" received posts of "+userID+"\n"+
								str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GetFriendList(userID: Int) =>
					// println("Asking for friend list")
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getFriendList?userID="+userID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println("user "+myID+" received friend list of "+userID+"\n"+
								str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GetAlbum(userID: Int, albumID: Int) =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getAlbum?userID="+userID+"&albumID="+albumID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							val album = str.entity.asString.parseJson.convertTo[Album]
							println("user "+myID+" received album of "+userID+
								"\nalbumID "+album.id+
								"\nlikes "+album.likes.mkString(","))
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "Post" =>
					var text: String = Random.alphanumeric.take(10).mkString
					// var text: String = "Post by user "+myID
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/post?userID="+myID+"&text="+text))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println("User "+myID+" successfully posted "+numOfPosts+1)
							numOfPosts = numOfPosts + 1
							// println(str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "AddFriend" =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/addFriend?userID="+myID+"&totalUsers="+numOfUsers))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "AddPage" =>
					var id: Int = numOfPages
					numOfPages = numOfPages + 1
					var name: String = "page-"+Random.nextInt(numOfUsers).toString
					val json = Page(id,myID,name).toJson.toString
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/addPage?pageID=",HttpEntity(MediaTypes.`application/json`, json)))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "AddAlbum" =>
					var likes: ArrayBuffer[String] = ArrayBuffer[String](Random.nextInt(numOfUsers).toString)
					var avgNumOfLikes = Random.nextInt(20)
					for(i <- 0 to avgNumOfLikes){
						likes += Random.nextInt(numOfUsers).toString
					}
					var picturelikes: ArrayBuffer[String] = ArrayBuffer[String](Random.nextInt(numOfUsers).toString)
					for(i <- 0 to avgNumOfLikes){
						likes += Random.nextInt(numOfUsers).toString
					}
					var id: Int = Random.nextInt(100)
					var pictures: ArrayBuffer[Picture] = ArrayBuffer[Picture](Picture(id,myID,("pic-"+Random.nextInt(numOfUsers).toString),likes.toArray))
					
					var avgNumOfPictures = Random.nextInt(10)
					for(i <- 0 to avgNumOfPictures){
						var picturelikes: ArrayBuffer[String] = ArrayBuffer[String](Random.nextInt(numOfUsers).toString)
						for(i <- 0 to avgNumOfLikes){
							likes += Random.nextInt(numOfUsers).toString
						}
						var id: Int = Random.nextInt(100)
						pictures += Picture(id,myID,("pic-"+Random.nextInt(numOfUsers).toString),likes.toArray)
						
					}
					val json = Album(numOfAlbums,myID, pictures.toArray, likes.toArray).toJson.toString
					// val json = new Album(numOfAlbums.toString,myID.toString,likes).toJson.toString
					// val json = AlbumWrapper(myID, numOfAlbums, new Album(numOfAlbums.toString,myID.toString,likes)).toJson.toString
					numOfAlbums = numOfAlbums + 1
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/addAlbum",HttpEntity(MediaTypes.`application/json`, json)))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}
			}
		}

		if(1>args.size){
			println("Please enter number of facebook users to start simulation!")
		} else {
			implicit val system = ActorSystem("ClientSystem")
			val clientMaster =system.actorOf(Props(new ClientMaster((args(0).toInt),system)),name="clientMaster")
			clientMaster ! "Start"
		}
		
	}	
}
