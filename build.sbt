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


ThisBuild / versionScheme := Some("early-semver")

// Was bare `scalaVersion := ...` (root-project-only) before the multi-module split below;
// moved to ThisBuild so coreApp/cli resolve the same Scala version instead of falling back
// to sbt's own default.
ThisBuild / scalaVersion := "2.12.17"

// Likewise moved to ThisBuild (was sdk-project-only): `update` is resolved independently
// per project, and coreApp/cli each pull org.ethereum:leveldbjni-all in transitively via
// dependsOn(sdk, ...) (com.halibobor's leveldbjni-all is meant to win instead - see the
// comment by the sdk project's own libraryDependencies) - without this exclusion applying
// to them too, their own `update` fails trying to resolve the unavailable
// org.ethereum:leveldbjni-all:1.18.3 artifact.
ThisBuild / excludeDependencies += ExclusionRule("org.ethereum", "leveldbjni-all")

// Likewise moved to ThisBuild for the same reason: coreApp/cli's own `update` resolves
// the same transitive graph and needs this resolver too.
ThisBuild / resolvers += "SCIJava" at "https://maven.scijava.org/content/repositories/public/"
// That is, to create a valid sbt build, all you've got to do is define the
// version of Scala you'd like your project to use.

// ============================================================================

// Lines like the above defining `scalaVersion` are called "settings". Settings
// are key/value pairs. In the case of `scalaVersion`, the key is "scalaVersion"
// and the value is "2.13.8"

// It's possible to define many kinds of settings, such as:

// Common test settings shared by every module whose tests run against a live Paideia
// session (root sdk and coreApp, which depends on sdk's own test fixtures via
// `% "test->test"`): sequential-friendly reporting (-oDF) plus parallel test-class
// execution, since PaideiaSessionFixture gives every suite its own isolated session.
lazy val commonTestSettings = Seq(
  testOptions in Test += Tests.Argument("-oDF"),
  Test / parallelExecution := true
)

lazy val sdk: Project = (project in file("."))
  .settings(
    name := "paideia-sdk",
    organization := "im.paideia",

    //githubOwner := "ergo-pad",
    //githubRepository := "paideia-sdk",

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
  )
  .settings(commonTestSettings)
  // Referenced by project id (LocalProject), not by the coreApp/cli vals themselves:
  // sdk is both the aggregate root AND a library coreApp/cli depend on
  // (dependsOn(sdk % ...) below), so sdk <-> coreApp/cli are mutually referential lazy
  // vals - aggregating the vals directly here would force coreApp/cli to initialize
  // while sdk itself is still initializing (coreApp's own dependsOn(sdk) forcing sdk
  // right back), which is an infinite loop (observed as a StackOverflowError loading
  // the build). Referencing by id sidesteps forcing the other vals from here.
  .aggregate(LocalProject("coreApp"), LocalProject("cli"))

// ergo-wallet 6.0.0 declares circe 0.13 while sigma-state 6.0.6 declares 0.14; upstream appkit ships that mix.
ThisBuild / evictionErrorLevel := Level.Warn

/** Framework-free state lifecycle (genesis seeding, restore/replay/checkpoint, read
  * models) on top of the sdk - see core-app/src/main/scala/im/paideia/app for the
  * ported paideia-state logic this wraps. Shares the sdk's own test fixtures
  * (PaideiaSessionFixture, PaideiaTestSuite, HttpClientTesting, ...) via the
  * `test->test` dependency below, so its suites can build DAOs/proposals/boxes the same
  * way the sdk's own governance suites do.
  */
lazy val coreApp: Project = (project in file("core-app"))
  .dependsOn(sdk % "compile->compile;test->test")
  .settings(
    name := "paideia-core-app"
  )
  .settings(commonTestSettings)

/** Thin command-line shell over coreApp: packaged as a self-contained fat JAR via
  * sbt-assembly (never published - see `publish / skip` below), with the protocol
  * instance's genesis conf and CLI defaults baked in as resources (see
  * cli/src/main/resources).
  */
lazy val cli: Project = (project in file("cli"))
  // test->test brings coreApp's (transitively sdk's) Test-scoped scalatest dependency
  // onto cli's own test classpath (ArgParserSuite) without cli needing to declare a
  // scalatest dependency of its own - see ArgParserSuite.
  .dependsOn(coreApp % "compile->compile;test->test")
  .settings(
    name := "paideia-cli",
    publish / skip := true,
    // Terminal QR rendering for the default ErgoPay tx-signing flow (Main.signAndSubmit)
    // - the only place this repo needs QR encoding, so it's scoped to the cli module
    // alone rather than added to the sdk or coreApp.
    libraryDependencies += "com.google.zxing" % "core" % "3.5.3",
    Compile / mainClass := Some("im.paideia.cli.Main"),
    assembly / mainClass := Some("im.paideia.cli.Main"),
    assembly / assemblyJarName := "paideia-cli.jar",
    assembly / assemblyMergeStrategy := {
      case "reference.conf" => MergeStrategy.concat
      case "module-info.class" => MergeStrategy.discard
      // Every dependency jar (appkit, shapeless, ...) carries its own META-INF/MANIFEST.MF;
      // without discarding these, MergeStrategy.first below would pick whichever
      // dependency's manifest happens to come first on the classpath instead of the
      // Main-Class-carrying manifest sbt-assembly itself synthesizes for the final jar.
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*)
          if xs.exists(x => x.endsWith(".SF") || x.endsWith(".DSA") || x.endsWith(".RSA")) =>
        MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  )
