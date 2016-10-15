name := """conductor"""

organization  := "com.flurdy"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += "flurdy-maven" at "http://dl.bintray.com/content/flurdy/maven"

libraryDependencies ++= {
   val akkaVersion = "2.4.11"
   Seq(
     "com.typesafe.akka" %% "akka-actor" % akkaVersion,
     "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
     "com.typesafe.akka" %% "akka-slf4j"    % akkaVersion,
     "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
     "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % "test",
     "org.scalatest"     %% "scalatest" % "2.2.6" % "test",
     "com.flurdy"        %% "sander" % "0.1.3",
     "ch.qos.logback"    %  "logback-classic" % "1.1.7",
     "org.mockito"       %  "mockito-all" % "1.10.19" % "test",
     "io.argonaut"       %% "argonaut" % "6.1",
      "com.spotify"      %  "docker-client" % "5.0.2"
   )
}
