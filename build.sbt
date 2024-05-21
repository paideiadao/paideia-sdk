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
    "io.github.k-singh" %% "plasma-toolkit" % "1.0.4",
    "com.typesafe" % "config" % "1.4.0",
    "com.github.tototoshi" %% "scala-csv" % "1.3.10",
    "org.scalatest" %% "scalatest-funsuite" % "3.2.13" % Test,
    "com.squareup.okhttp3" % "mockwebserver" % "3.12.0" % Test
)

dependencyOverrides += "org.scorexfoundation" % "sigma-state_2.12" % "5.0.14-39-8af5260b-SNAPSHOT"

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Bintray" at "https://jcenter.bintray.com/"
)

testOptions in Test += Tests.Argument("-oDF")

Test / parallelExecution := false
