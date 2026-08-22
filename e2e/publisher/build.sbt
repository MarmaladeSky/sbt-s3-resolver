organization := "e2e.test"
name := "fixture-library"
version := "1.0.0"
scalaVersion := "3.8.4"

publishMavenStyle := true
publishTo := Some("E2E repository" at "s3://sbt-s3-resolver-e2e/releases")
