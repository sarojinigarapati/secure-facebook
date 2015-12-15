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
import java.security._
import java.security.spec._
import javax.crypto._
import javax.crypto.spec._
import org.apache.commons.codec.binary.Base64

object project4 {
	def main(args: Array[String]){

		val simulationTime: Int = 20000 // in milliseconds
		val start: Long = System.currentTimeMillis
		var numOfPages: Int = 0

		case class Stat(userPosts: Int)
		case class GetProfile(userID: Int)
		case class GetPicture(userID: Int, picID: Int)
		case class GetAlbum(userID: Int, albumID: Int)
		case class GetPosts(userID: Int)
		case class GetFriendList(userID: Int)
		case class GetPage(userID: Int, pageID: Int)
		case class GiveAccessToFriend(frdID: Int, bytes: Array[Byte])
		case class AcceptFriend(frdID: Int)
		case class PictureAccess(frdList: Array[Int], frdPubKeys: Array[Array[Byte]])

		case class Profile(id: String, first_name: String, last_name: String, age: String, email: String, gender: String, relation_status: String)
		case class SignUp(id: Int, pkey: Array[Byte], profile: Array[Byte])
		case class Pic(id: Int, from: Int, data: Array[Byte], picAcl: Array[Int], picAesKeys: Array[Array[Byte]])
		// case class PicFrdList(frdList: Array[Int], frdPubKeys: Array[String])
		case class PicFrdList(frdList: Array[Int], frdPubKeys: Array[Array[Byte]])
		case class Picture(id: Int, from: Int, text: String, likes: Array[String])
		case class Album(id: Int, from: Int, pictures: Array[Picture], likes: Array[String])
		case class Page(id: Int, from: Int, name: String)
		case class AccessFriend(userID: Int, frdID: Int, bytes: Array[Byte])
		case class SendProfile(profileAesKey: String, profileBytes: Array[Byte])
		case class SendPic(picAesKey: Array[Byte], data: Array[Byte])
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
			implicit val SendPicFormat = jsonFormat2(SendPic)
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
					pipeline(Post("http://localhost:8080/facebook/shutdown"))
					context.system.shutdown()
			}
		}

		class FacebookAPI(system: ActorSystem, myID: Int, numOfUsers: Int) 
			extends Actor{

			// var kpg: KeyPairGenerator =_
			var kp: KeyPair =_
			var aesKeyProfile: SecretKey =_
			var photoAesKeys: ArrayBuffer[SecretKey] = new ArrayBuffer[SecretKey]()
			var numOfPosts: Int =_
			var numOfPics: Int = 0
			var numOfAlbums: Int =_
			var cancels: ArrayBuffer[Cancellable] = new ArrayBuffer[Cancellable]()

			import system.dispatcher
			val pipeline = sendReceive

			def receive = {

				case "SignUp" =>
					// Generate RSA keys
					val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
					kpg.initialize(2048)
					kp = kpg.genKeyPair()

					 // Generate AES key
				    val kgen: KeyGenerator = KeyGenerator.getInstance("AES")
				    kgen.init(128)
				    aesKeyProfile = kgen.generateKey()

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

					// Encrypt profile
					val profile = Profile(id, first_name, last_name, age, email, gender, relation_status).toJson.toString
					val profileBytes = profile.getBytes("UTF-8")

					// Encrypt with AES symmetric key
					var aescipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
					aescipher.init(Cipher.ENCRYPT_MODE, aesKeyProfile)
					val bytes = aescipher.doFinal(profileBytes)

					// Encrypt with RSA private key
					// var cipher: Cipher = Cipher.getInstance("RSA")
					// cipher.init(Cipher.ENCRYPT_MODE, kp.getPrivate())
					// val bytes = cipher.doFinal(profileBytes)

					val json = SignUp(myID, kp.getPublic().getEncoded(), bytes).toJson.toString
					// println("profile as json string = "+profile)
					// val profile = SignUp(myID, Profile(id, first_name, last_name, 
					// 				age, email, gender, relation_status))
					
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/addProfile",HttpEntity(MediaTypes.`application/json`, json)))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
							// var bytes: Array[Byte] = str.entity.asString.parseJson.convertTo[Array[Byte]]
							
							// // Decrypt Data
							// var aescipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
							// aescipher.init(Cipher.DECRYPT_MODE, aesKeyProfile)
							// val decryptedBytes = aescipher.doFinal(bytes)
							// val profile = (new String(decryptedBytes, "UTF-8")).parseJson.convertTo[Profile]
							// println(profile.toJson)

							// val json = (cipher.doFinal(bytes)).toString.parseJson.convertTo[SignUp]
							self ! GetProfile(myID)
							self ! "DoActivity"
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "DoActivity" =>
					system.scheduler.scheduleOnce(simulationTime milliseconds, self, "StopActivity")
					// cancelStop = system.scheduler.schedule(0 milliseconds,100 milliseconds,self,"Stop")
					
					cancels += system.scheduler.schedule(2000 milliseconds,4000 milliseconds,self,"AddFriend")
					cancels += system.scheduler.schedule(3000 milliseconds,4000 milliseconds,self,"AcceptFrdRequests")
					cancels += system.scheduler.schedule(3000 milliseconds,5000 milliseconds,self,"AddPicture")
					cancels += system.scheduler.schedule(4000 milliseconds,3000 milliseconds,self,GetProfile(Random.nextInt(numOfUsers)))
					cancels += system.scheduler.schedule(3500 milliseconds,5000 milliseconds,self,GetPicture(Random.nextInt(numOfUsers),Random.nextInt(5)))
					// cancels += system.scheduler.schedule(3500 milliseconds,5000 milliseconds,self,GetPicture(myID,Random.nextInt(5)))
					// cancels += system.scheduler.schedule(0 milliseconds,8000 milliseconds,self,"AddAlbum")
					// cancels += system.scheduler.schedule(90 milliseconds,7000 milliseconds,self,"AddPage")
					// cancels += system.scheduler.schedule(30 milliseconds,3000 milliseconds,self,GetAlbum(myID, 0))
					// if(myID % 2 == 0 ){
					// 	cancels += system.scheduler.schedule(150 milliseconds,2000 milliseconds,self,"Post")
					// } else {
					// 	cancels += system.scheduler.schedule(180 milliseconds,4000 milliseconds,self,"Post")
					// }

				case "StopActivity" =>
					if(System.currentTimeMillis - start > simulationTime){ // in milliseconds
						// println("Shutting down the system!!")
						for(i <- 0 until cancels.length){
							cancels(i).cancel()
						}
						self ! GetFriendList(myID)
						context.parent ! Stat(numOfPosts)
					}

				case GetProfile(getID: Int) =>
					println("user "+myID+" requesting profile of "+getID)
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getProfile?userID="+myID+"&getID="+getID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							if(str.entity.asString == "PermissionDenied"){
								println("PermissionDenied for user "+myID+" to access profile of "+getID)
							} else {
								if(getID == myID){
									var data: SendProfile = str.entity.asString.parseJson.convertTo[SendProfile]

									// Decrypt Data
									var aescipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
									aescipher.init(Cipher.DECRYPT_MODE, aesKeyProfile)
									val decryptedBytes = aescipher.doFinal(data.profileBytes)
									val profile = (new String(decryptedBytes, "UTF-8")).parseJson.convertTo[Profile]
									println(profile.toJson)
								} else {
									var data: SendProfile = str.entity.asString.parseJson.convertTo[SendProfile]

									// First decrypt with your private key to get AES secret key
									var cipher: Cipher = Cipher.getInstance("RSA")
									// cipher.init(Cipher.DECRYPT_MODE, kp.getPrivate())
									// val profileAesKey = Base64.decodeBase64(data.profileAesKey)
									// val bytes = cipher.doFinal(profileAesKey)
									// val aeskeySpec: SecretKeySpec = new SecretKeySpec(bytes, "AES")
									cipher.init(Cipher.UNWRAP_MODE, kp.getPrivate())
									val profileAesKey = Base64.decodeBase64(data.profileAesKey)
									val aeskeySpec = (cipher.unwrap(profileAesKey,"AES",Cipher.SECRET_KEY))

									// Decrypt Data
									var aescipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
									aescipher.init(Cipher.DECRYPT_MODE, aeskeySpec)
									val decryptedBytes = aescipher.doFinal(data.profileBytes)
									val profile = (new String(decryptedBytes, "UTF-8")).parseJson.convertTo[Profile]
									println(profile.toJson)
								}
							}
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "AddFriend" =>
					var frdID: Int = Random.nextInt(numOfUsers)
					while(frdID == myID){
						frdID = Random.nextInt(numOfUsers)
					}
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/addFriend?userID="+myID+"&frdID="+frdID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
							if(str.entity.asString == "AlreadyFriend"){
								println("user "+frdID+" is already a friend to user "+myID)
							} else {
								println("users "+myID+" and "+frdID+ " became friends!!")
								var bytes: Array[Byte] = str.entity.asString.parseJson.convertTo[Array[Byte]]
								// var bytes: Array[Byte] = str.entity.data.toByteArray
								// println(bytes.toJson)
								// val kf: KeyFactory = KeyFactory.getInstance("RSA"); // or "EC" or whatever
								// val frdPublicKey: PublicKey = kf.generatePublic(new X509EncodedKeySpec(bytes))
								self ! GiveAccessToFriend(frdID, bytes)
							}
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GiveAccessToFriend(frdID: Int, bytes: Array[Byte]) =>
					// Generate the friends public key
					val kf: KeyFactory = KeyFactory.getInstance("RSA"); // or "EC" or whatever
					val frdPublicKey: PublicKey = kf.generatePublic(new X509EncodedKeySpec(bytes))

					// Encrypt your profile AES secret key with frd's public key
					var rsaCipher: Cipher = Cipher.getInstance("RSA")
					// rsaCipher.init(Cipher.ENCRYPT_MODE, frdPublicKey)
					rsaCipher.init(Cipher.WRAP_MODE, frdPublicKey)
					// val encryptedString = Base64.encodeBase64String(rsaCipher.doFinal(aesKeyProfile.getEncoded()))
					// val encryptedString = rsaCipher.doFinal(aesKeyProfile.getEncoded())
					val encryptedString = rsaCipher.wrap(aesKeyProfile)
					// Send the encrypted AES secret key for the server to store
					val json = AccessFriend(myID, frdID, encryptedString).toJson.toString
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/accessFriend",HttpEntity(MediaTypes.`application/json`, json)))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							println(str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "AcceptFrdRequests" =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/acceptFriendList?userID="+myID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							var acceptList: Array[Int] = str.entity.asString.parseJson.convertTo[Array[Int]]
							for(i <- 0 until acceptList.length){
								self ! AcceptFriend(acceptList(i))
							}
							// println(str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case AcceptFriend(frdID: Int) =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/acceptFriend?userID="+myID+"&frdID="+frdID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
							if(str.entity.asString == "AlreadyFriend"){
								println("user "+frdID+" is already a friend to user "+myID)
							} else {
								println("users "+myID+" and "+frdID+ " became friends!!")
								var bytes: Array[Byte] = str.entity.asString.parseJson.convertTo[Array[Byte]]
								// var bytes: Array[Byte] = str.entity.data.toByteArray
								// println(bytes.toJson)
								// val kf: KeyFactory = KeyFactory.getInstance("RSA"); // or "EC" or whatever
								// val frdPublicKey: PublicKey = kf.generatePublic(new X509EncodedKeySpec(bytes))
								self ! GiveAccessToFriend(frdID, bytes)
							}
						case Failure(error) =>
							println(error+"something wrong")
					}

				case "AddPicture" =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/initPicture?userID="+myID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							var picFrdList: PicFrdList = str.entity.asString.parseJson.convertTo[PicFrdList]
							self ! PictureAccess(picFrdList.frdList, picFrdList.frdPubKeys)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case PictureAccess(frdList: Array[Int], frdPubKeys: Array[Array[Byte]]) =>
					// Generate AES secret key
					val kgen: KeyGenerator = KeyGenerator.getInstance("AES")
				    kgen.init(128)
				    photoAesKeys += kgen.generateKey()
					// Create a picture and encrypt it
					var text: String = "Picture-"+numOfPics.toString
					var aescipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
					aescipher.init(Cipher.ENCRYPT_MODE, photoAesKeys(numOfPics))
					val data = aescipher.doFinal(text.getBytes("UTF-8"))
					// Create a list of encrypted keys who can access the picture
					var picAcl: ArrayBuffer[Int] = ArrayBuffer[Int]()
					var picAesKeys: ArrayBuffer[Array[Byte]] = ArrayBuffer[Array[Byte]]()
					for(i <- 0 until frdList.length){
						// if(Random.nextInt(100) < 50){
							picAcl += frdList(i)
							// var bytes: Array[Byte] = Base64.decodeBase64(frdPubKeys(i))

							// Generate the friends public key
							val kf: KeyFactory = KeyFactory.getInstance("RSA"); // or "EC" or whatever
							val frdPublicKey: PublicKey = kf.generatePublic(new X509EncodedKeySpec(frdPubKeys(i)))

							// Encrypt your profile AES secret key with frd's public key
							var rsaCipher: Cipher = Cipher.getInstance("RSA")
							// rsaCipher.init(Cipher.ENCRYPT_MODE, frdPublicKey)
							// picAesKeys += Base64.encodeBase64String(rsaCipher.doFinal(photoAesKeys(numOfPics).getEncoded()))

							rsaCipher.init(Cipher.WRAP_MODE, frdPublicKey)
							picAesKeys += (rsaCipher.wrap(photoAesKeys(numOfPics)))
						// }
					}
					// picAcl += myID
					// var rsaCipher: Cipher = Cipher.getInstance("RSA")
					// // rsaCipher.init(Cipher.ENCRYPT_MODE, kp.getPublic())
					// // picAesKeys += Base64.encodeBase64String(rsaCipher.doFinal(photoAesKeys(numOfPics).getEncoded()))

					// rsaCipher.init(Cipher.WRAP_MODE, kp.getPublic())
					// picAesKeys += (rsaCipher.wrap(photoAesKeys(numOfPics)))

					// Send it to the server for storing
					val json = Pic(numOfPics, myID, data, picAcl.toArray, picAesKeys.toArray).toJson.toString
					numOfPics = numOfPics + 1
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/addPicture",HttpEntity(MediaTypes.`application/json`, json)))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							// println(str.entity.asString)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GetPicture(userID: Int, picID: Int) =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getPicture?userID="+myID+"&frdID="+userID+"&picID="+picID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							if(str.entity.asString == "PermissionDenied"){
								println("PermissionDenied for user "+myID+" to access picture "+picID+" of user "+userID)
							} else if(str.entity.asString == "PictureNotFound"){
								println("No picture "+picID+" of user "+userID+" exists!!")
							// } else if(userID == myID) {
							// 	var sendPic: SendPic = str.entity.asString.parseJson.convertTo[SendPic]

							// 	// Decrypt Data
							// 	var aescipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
							// 	aescipher.init(Cipher.DECRYPT_MODE, photoAesKeys(picID))
							// 	val decryptedBytes = aescipher.doFinal(sendPic.data)
							// 	val pic = (new String(decryptedBytes, "UTF-8"))
							// 	println(pic)
							} else {
								println("Getting picture "+picID+" of user "+userID+" for user "+myID)
								var sendPic: SendPic = str.entity.asString.parseJson.convertTo[SendPic]

								// First decrypt with your private key to get AES secret key
								var cipher: Cipher = Cipher.getInstance("RSA")
								// cipher.init(Cipher.DECRYPT_MODE, kp.getPrivate())
								// val aesKey = Base64.decodeBase64(sendPic.picAesKey)
								// val bytes = cipher.doFinal(aesKey)
								// val aeskeySpec: SecretKeySpec = new SecretKeySpec(bytes, "AES")

								cipher.init(Cipher.UNWRAP_MODE, kp.getPrivate())
								// val aesKey = Base64.decodeBase64(sendPic.picAesKey)
								val aeskeySpec = (cipher.unwrap(sendPic.picAesKey,"AES",Cipher.SECRET_KEY))

								// Decrypt Data
								var aescipher: Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
								aescipher.init(Cipher.DECRYPT_MODE, aeskeySpec)
								val decryptedBytes = aescipher.doFinal(sendPic.data)
								val pic = (new String(decryptedBytes, "UTF-8"))
								println(pic)
							}
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GetPage(userID: Int, pageID: Int) =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getPage?userID="+userID+"&pageID="+pageID))
					responseFuture onComplete {
						case Success(str: HttpResponse) =>
							val page = str.entity.asString.parseJson.convertTo[Page]
							println("user "+myID+" received page of "+userID+"\n"+
								"\npageID "+page.id+
								"\nname "+page.name)
						case Failure(error) =>
							println(error+"something wrong")
					}

				case GetPosts(userID: Int) =>
					val responseFuture = pipeline(Post("http://localhost:8080/facebook/getPage?userID="+userID))
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
							var frdListArray: Array[Int] = str.entity.asString.parseJson.convertTo[Array[Int]]
							println(frdListArray.toJson)
							// println("user "+myID+" received friend list of "+userID+"\n"+
							// 	str.entity.asString)
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
