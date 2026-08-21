organization := "digital.junkie"

name := "sbt-s3-resolver"

description := "SBT S3 Resolver Plugin"

homepage := Some(uri("https://github.com/MarmaladeSky/sbt-s3-resolver"))

licenses := Seq(License.Apache2)

scmInfo := Some(
  ScmInfo(
    uri("https://github.com/MarmaladeSky/sbt-s3-resolver"),
    "scm:git:https://github.com/MarmaladeSky/sbt-s3-resolver.git",
    "scm:git:git@github.com:MarmaladeSky/sbt-s3-resolver.git"
  )
)

developers := List(
  Developer(
    id = "88D15D7AF7672C866A8F839C56B2CCA5F83AECB4",
    name = "David Akermann",
    email = "david@junkie.digital",
    url = uri("https://github.com/MarmaladeSky")
  )
)

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
val munitVersion = "1.1.1"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % amazonSDKVersion,
  "com.amazonaws" % "aws-java-sdk-sts" % amazonSDKVersion,
  "org.apache.ivy" % "ivy" % ivyVersion,
  "org.scalameta" %% "munit" % munitVersion % Test
)

ThisBuild / versionScheme := Some("semver-spec")

ThisBuild / publishTo := {
  if (isSnapshot.value) Some(Resolver.sonatypeCentralSnapshots)
  else localStaging.value
}

publishMavenStyle := true

Global / pgpSigningKey := Some("5F0ECF7B017D1A2D")
