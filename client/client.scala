import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Cancellable;
import scala.util.Random
import scala.collection.mutable.ListBuffer
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

		val start: Long = System.currentTimeMillis
		case class Stat(userPosts: Int)
		case class GetProfile(userID: Int)
		case class GetPosts(userID: Int)
		case class GetFriendList(userID: Int)
		case class dummy(first: String, second: String)
		case class BigDummy(userID: String, d: dummy)
		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, profile: Profile)
		// class Color(val name: String, val red: Int, val green: Int, val blue: Int)

// class Post(input_id: String, input_message: String, input_from: String, input_to: String, input_taggers: ArrayBuffer[String]) extends java.io.Serializable {
//    var id: String = input_id
//    var message: String = input_message
//    var from: String = input_from
//    var to: String = input_to
//    var tags: ArrayBuffer[String] = input_taggers
//  }
// implicit object PostJsonFormat extends JsonFormat[FacebookServer.Post] {
   
//    def write(post: FacebookServer.Post) = JsObject(
//      "id" -> JsString(post.id),
//      "message" -> JsString(post.message),
//      "from" -> JsString(post.from),
//      "to" -> JsString(post.to),
//      "tags" -> JsArray(post.tags.map(_.toJson).toVector))

//    def read(value: JsValue) = {
//      value.asJsObject.getFields("id", "message", "from", "to", "tags") match {
//        case Seq(JsString(id), JsString(message), JsString(from), JsString(to), JsArray(tags)) =>
//          new FacebookServer.Post(id, message, from, to, tags.map(_.convertTo[String]).to[ArrayBuffer])
//        case Seq(JsString(message), JsString(from), JsArray(tags)) =>
//          new FacebookServer.Post(null, message, from, null, tags.map(_.convertTo[String]).to[ArrayBuffer])
//        case _ =>
//          throw new DeserializationException("Invalid post")
//      }
//    }
//  }
		object MyJsonProtocol extends DefaultJsonProtocol {
			implicit val ObjFormat = jsonFormat7(Profile)
			implicit val SignUpFormat = jsonFormat2(SignUp)
			implicit val dummyFormat = jsonFormat2(dummy)
			implicit val BigDummyFormat = jsonFormat2(BigDummy)
			// implicit object ColorJsonFormat extends RootJsonFormat[Color] {
			//     def write(c: Color) =
			//       JsArray(JsString(c.name), JsNumber(c.red), JsNumber(c.green), JsNumber(c.blue))

			//     def read(value: JsValue) = value match {
			//       case JsArray(Vector(JsString(name), JsNumber(red), JsNumber(green), JsNumber(blue))) =>
			//         new Color(name, red.toInt, green.toInt, blue.toInt)
			//       case _ => deserializationError("Color expected")
			//     }
			// }
		}

		import MyJsonProtocol._
		
		class ClientMaster(numOfUsers: Int, system: ActorSystem) extends Actor{

			var count: Int = _
			var totalPosts: Int = _

			import system.dispatcher
			val pipeline = sendReceive
			
			def receive = {
				case "start" =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/start?numOfUsers="+numOfUsers))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println(str.entity.asString)
							self ! "StartSimulation"
						case Failure(error) =>
							println(error+"something wrong")
					}
				
				case "StartSimulation" =>
					println("Server is initialized! We can start the simulation!")
					for(i <- 0 until numOfUsers){
						var myID: String = i.toString
						val actor = context.actorOf(Props(new FacebookAPI(system, i, numOfUsers)),name=myID)
						actor ! "InitUser"
						actor ! "Stop"
					}

				case Stat(userPosts: Int) =>
					totalPosts = totalPosts + userPosts
					count = count + 1
					if(count == numOfUsers){
						println("Total number of posts = "+totalPosts)
						system.scheduler.scheduleOnce(2000 milliseconds, self, "ShutDown")
					}

				case "ShutDown" =>
					context.system.shutdown()
			}
		}

		class FacebookAPI(system: ActorSystem, myID: Int, numOfUsers: Int) 
			extends Actor{

			var numOfPosts: Int =_
			// var cancels = new ListBuffer[Cancellable]()
			var cancelStop: Cancellable =_
			var cancelPosts1: Cancellable =_
			var cancelPosts2: Cancellable =_
			var cancelPosts3: Cancellable =_

			import system.dispatcher
			val pipeline = sendReceive

			def receive = {

				case "InitUser" =>
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
					println("user "+myID+" doing sign up")
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
					cancelStop = system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Stop")
					if(myID % 2 == 0 ){
						cancelPosts1 = system.scheduler.schedule(0 milliseconds,50 milliseconds,self,"Post")
					} else {
						cancelPosts2 = system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Post")
					}
					cancelPosts3 = system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"AddFriend")

					// cancels += system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Stop")
					// if(myID % 2 == 0 ){
					// 	cancels += system.scheduler.schedule(0 milliseconds,50 milliseconds,self,"Post")
					// } else {
					// 	cancels += system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Post")
					// }
					// // cancels += system.scheduler.schedule(0 milliseconds,1000 milliseconds,self,"AddFriend")
					
				case "Stop" =>
					if(System.currentTimeMillis - start > 10000){ // in milliseconds
						// println("Shutting down the system!!")
						cancelStop.cancel()
						if(myID % 2 == 0 ){
							cancelPosts1.cancel()
						} else {
							cancelPosts2.cancel()
						}
						cancelPosts3.cancel()
						// self ! GetPosts(myID)
						self ! GetFriendList(myID)
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
								// str.entity.asString.parseJson.convertTo[Profile].age)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GetFriendList(userID: Int) =>
					// println("Asking for friend list")
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getFriendList?userID="+userID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println("user "+myID+" received posts of "+userID+"\n"+
								str.entity.asString)
								// str.entity.asString.parseJson.convertTo[Profile].age)
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
							println(str.entity.asString)
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
			clientMaster ! "start"
		}
		
	}	
}
