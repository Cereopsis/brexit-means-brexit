
name := "functional-tap"
version := "1.0"
scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
    "org.scalaz" %% "scalaz-zio" % "0.16",
    "org.scalactic" %% "scalactic" % "3.0.5",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

lazy val root = (project in file("."))

scalacOptions in ThisBuild := Seq("-unchecked", "-deprecation")

fork in run := true

