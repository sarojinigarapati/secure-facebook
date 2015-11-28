    name := "My Project"
     
    version := "1.0"
     
    scalaVersion := "2.11.4"
     
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

    resolvers += "spray repo" at "http://repo.spray.io"

    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.6"

	libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.3.6"

	libraryDependencies += "io.spray" %% "spray-routing" % "1.3.2"

	libraryDependencies += "io.spray" %% "spray-can" % "1.3.2"

	libraryDependencies += "io.spray" %% "spray-json" % "1.3.2"

   
