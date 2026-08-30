import sbt.url
// The simplest possible sbt build file is just one line:

inThisBuild(List(
  organization := "im.paideia",
  homepage := Some(url("https://paideia.im")),
  // Alternatively License.Apache2 see https://github.com/sbt/librarymanagement/blob/develop/core/src/main/scala/sbt/librarymanagement/License.scala
  licenses := List(License.MIT),
  developers := List(
    Developer(
      "luivatra",
      "Rob van Leeuwen",
      "luivatra@gmail.com",
      url("https://github.com/luivatra")
    )
  )
))

sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

scalaVersion := "2.12.17"
// That is, to create a valid sbt build, all you've got to do is define the
// version of Scala you'd like your project to use.

// ============================================================================

// Lines like the above defining `scalaVersion` are called "settings". Settings
// are key/value pairs. In the case of `scalaVersion`, the key is "scalaVersion"
// and the value is "2.13.8"

// It's possible to define many kinds of settings, such as:

name := "paideia-sdk"
organization := "im.paideia"

//githubOwner := "ergo-pad"
//githubRepository := "paideia-sdk"

// Note, it's not required for you to define these three settings. These are
// mostly only necessary if you intend to publish your library's binaries on a
// place like Sonatype.


// Want to use a published library in your project?
// You can define other libraries as dependencies in your build like this:

libraryDependencies ++= Seq(
    //"org.scala-lang.modules" %% "scala-parser-combinators" % "2.1.1",
    "io.github.k-singh" %% "plasma-toolkit" % "1.1.0",
    "org.ergoplatform" %% "ergo-appkit" % "6.0.1",
    // org.ethereum:leveldbjni-all (pulled in transitively) is not on any public repo;
    // com.halibobor bundles LevelDB 1.23, which reads the .ldb files written by the org.ethereum build (1.18)
    // that production ran; io.github.tronprotocol bundles an older LevelDB that only knows .sst files.
    "com.halibobor" % "leveldbjni-all" % "1.23.2",
    "com.typesafe" % "config" % "1.4.0",
    "commons-io" % "commons-io" % "2.11.0",
    "com.github.tototoshi" %% "scala-csv" % "1.3.10",
    "org.scalatest" %% "scalatest-funsuite" % "3.2.13" % Test,
    "com.squareup.okhttp3" % "mockwebserver" % "3.12.0" % Test
)


excludeDependencies += ExclusionRule("org.ethereum", "leveldbjni-all")

// ergo-wallet 6.0.0 declares circe 0.13 while sigma-state 6.0.6 declares 0.14; upstream appkit ships that mix.
ThisBuild / evictionErrorLevel := Level.Warn

resolvers += "SCIJava" at "https://maven.scijava.org/content/repositories/public/"

testOptions in Test += Tests.Argument("-oDF")

Test / parallelExecution := false
