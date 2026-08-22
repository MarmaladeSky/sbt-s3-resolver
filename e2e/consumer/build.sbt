scalaVersion := "3.8.4"

resolvers += "E2E repository" at "s3://sbt-s3-resolver-e2e/releases"

libraryDependencies += "e2e.test" %% "fixture-library" % "1.0.0"
