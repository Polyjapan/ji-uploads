import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbt.Keys.{libraryDependencies, resolvers}

ThisBuild / organization := "ch.japanimpact"
ThisBuild / version      := "1.1.4"
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "ch.japanimpact" %% "jiauthframework" % "2.0-SNAPSHOT",
)
ThisBuild / resolvers += "Japan Impact Releases" at "https://repository.japan-impact.ch/releases"
ThisBuild / resolvers += "Japan Impact Snapshots" at "https://repository.japan-impact.ch/snapshots"

lazy val api = (project in file("api/"))
  .settings(
    name := "ji-uploads-api",
    publishArtifact in(Compile, packageDoc) := false,
    publishArtifact in(Compile, packageSrc) := false,
    publishArtifact in(Compile, packageBin) := true,
    libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.8.1",
    libraryDependencies += "com.pauldijou" %% "jwt-play-json" % "4.2.0",
    libraryDependencies += "com.google.inject" % "guice" % "4.2.2",
    publishTo := { Some("Japan Impact Repository" at { "https://repository.japan-impact.ch/" + ( if (isSnapshot.value) "snapshots" else "releases" ) } ) },
    credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
)


lazy val root = (project in file("."))
  .enablePlugins(PlayScala, JDebPackaging, SystemdPlugin, JavaServerAppPackaging, DockerPlugin)
  .settings(
    name := "ji-uploads",
    version := "1.1-b",
    libraryDependencies ++= Seq(jdbc, evolutions, ehcache, ws, specs2 % Test, guice),

    maintainer in Linux := "Louis Vialar <louis.vialar@japan-impact.ch>",
    packageSummary in Linux := "Scala Backend for Japan Impact Uploads",
    packageDescription := "Scala Backend for Japan Impact Uploads",
    debianPackageDependencies := Seq("java8-runtime-headless"),

    libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.4",
    libraryDependencies += "com.typesafe.play" %% "play-json-joda" % "2.8.1",
    libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.34",
    libraryDependencies += "com.pauldijou" %% "jwt-play" % "4.2.0",

    resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/",
    resolvers += Resolver.mavenCentral,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation"
    ),

    javaOptions in Universal ++= Seq(
      // Provide the PID file
      s"-Dpidfile.path=/dev/null",

      // Set the configuration to the production file
      s"-Dconfig.file=/etc/${packageName.value}/application.conf",

      // Apply DB evolutions automatically
      "-DapplyEvolutions.default=true"
    ),

    dockerExposedPorts in Docker := Seq(80),
    dockerUsername := Some("polyjapan"),
    // Install the required "file" package
    dockerCommands := dockerCommands.value.flatMap {
      case c @ Cmd("USER", "root") =>
        Seq(c,
          ExecCmd("RUN", "apt", "update"),
          ExecCmd("RUN", "apt", "install", "-y", "file")
        )

      case other => Seq(other)
    }
  )
  .aggregate(api)
  .dependsOn(api)

