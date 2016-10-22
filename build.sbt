// Name of the project
name := "reggatad-client"

// Project version
version := "0.1-SNAPSHOT"

// Version of Scala used by the project
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scalafx" %% "scalafx" % "8.0.92-R10",
  "com.typesafe.play" % "play-json_2.11" % "2.5.9"
)

//Add Javafx8 library
unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/ext/jfxrt.jar"))

// Fork a new JVM for 'run' and 'test:run', to avoid JavaFX double initialization problems
fork := true
