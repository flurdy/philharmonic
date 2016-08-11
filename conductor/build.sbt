name := """conductor"""

organization  := "com.flurdy"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

resolvers += "flurdy-maven" at "http://dl.bintray.com/content/flurdy/maven"

libraryDependencies ++= {
   val akkaVersion = "2.4.8"
   Seq(
     "com.typesafe.akka" %% "akka-actor" % akkaVersion,
     "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
     "com.typesafe.akka" %% "akka-slf4j"    % akkaVersion,
     "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
     "org.scalatest"     %% "scalatest" % "2.2.6" % "test",
     "me.lessis"         %% "tugboat" % "0.2.0" exclude("org.slf4j","slf4j-log4j12"),
     "com.flurdy"        %% "sander" % "0.1.2",
     "ch.qos.logback"    %  "logback-classic" % "1.1.7",
     "org.mockito" % "mockito-all" % "1.10.19" % "test"
   )
}
