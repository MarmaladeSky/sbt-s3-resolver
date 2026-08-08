
//
// For sbt-sonatype
//
organization := "digital.junkie"

publishMavenStyle := true

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

import xerial.sbt.Sonatype._
sonatypeProjectHosting := Some(GitHubHosting("MarmaladeSky", "sbt-s3-resolver", "David Akermann", "david@junkie.digital"))

//
// For sbt-pgp
//
// TODO: replace with your own 40-character GPG key fingerprint before releasing.
// Find it with: gpg --list-secret-keys --keyid-format=long --fingerprint
usePgpKeyHex("REPLACE_WITH_YOUR_GPG_FINGERPRINT")

//
// For sbt-release
//
import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("^ test"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("^ publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
