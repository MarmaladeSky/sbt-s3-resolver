organization := "digital.junkie"

name := "sbt-s3-resolver"

description := "SBT S3 Resolver Plugin"

scalaVersion := "3.8.4"

scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-unchecked",
  "-deprecation",
  "-language:implicitConversions",
  "-feature",
  "-Wshadow:all"
)

enablePlugins(SbtPlugin)

val amazonSDKVersion = "1.12.797"
val ivyVersion = "2.6.0"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % amazonSDKVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % amazonSDKVersion,
  "org.apache.ivy" % "ivy" % ivyVersion
)

ThisBuild / versionScheme := Some("semver-spec")
