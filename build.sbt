ThisBuild / scalaVersion := "2.13.10"

ThisBuild / version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := """file-sharing""",
    libraryDependencies ++= Seq(
      guice,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
      "org.mockito" %% "mockito-scala" % "1.17.12" % Test,
      "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "6.0.0",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.7.0",
      "com.typesafe.akka" %% "akka-serialization-jackson" % "2.7.0",
      "com.typesafe.akka" %% "akka-slf4j" % "2.7.0",
      "com.github.etaty" %% "rediscala" % "1.9.0",
      "org.mindrot" % "jbcrypt" % "0.4"
    )
  )