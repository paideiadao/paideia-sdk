import sbt.url
// The simplest possible sbt build file is just one line:

scalaVersion := "2.12.16"
// That is, to create a valid sbt build, all you've got to do is define the
// version of Scala you'd like your project to use.

// ============================================================================

// Lines like the above defining `scalaVersion` are called "settings". Settings
// are key/value pairs. In the case of `scalaVersion`, the key is "scalaVersion"
// and the value is "2.13.8"

// It's possible to define many kinds of settings, such as:

name := "paideia-sdk"
organization := "ergo-pad"
version := "0.0.1"

//githubOwner := "ergo-pad"
//githubRepository := "paideia-sdk"

// Note, it's not required for you to define these three settings. These are
// mostly only necessary if you intend to publish your library's binaries on a
// place like Sonatype.


// Want to use a published library in your project?
// You can define other libraries as dependencies in your build like this:

libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-parser-combinators" % "2.1.1",
    "io.github.getblok-io" % "getblok_plasma_2.12" % "1.0.0",
    "com.typesafe" % "config" % "1.4.0",
    "org.scalatest" %% "scalatest-funsuite" % "3.2.13" % Test,
    "com.squareup.okhttp3" % "mockwebserver" % "3.12.0" % Test
)

dependencyOverrides += "org.ergoplatform" %% "ergo-appkit" % "5.0.0"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Bintray" at "https://jcenter.bintray.com/"
)

testOptions in Test += Tests.Argument("-oDF")

Test / parallelExecution := false
