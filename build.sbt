name := "akka-clustering"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % "2.4.11",
  "com.typesafe.akka" %% "akka-cluster-metrics" % "2.4.11"
)