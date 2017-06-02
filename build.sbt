name := """my-first-app"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  "org.bouncycastle" % "bcprov-jdk15on" % "1.54",
  "com.google.guava" % "guava" % "19.0",
  "mysql" % "mysql-connector-java" % "6.0.5",
  "org.apache.commons" % "commons-lang3" % "3.5",
  "com.google.code.gson" % "gson" % "2.8.0",
  "org.apache.commons" % "commons-io" % "1.3.2",
  "org.apache.commons" % "commons-email" % "1.4",
  "javax.json" % "javax.json-api" % "1.0",
  "org.glassfish" % "javax.json" % "1.0.4",
  "org.mapdb" % "mapdb" % "3.0.4"
)
