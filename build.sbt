ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.dapperware"
ThisBuild / organizationName := "Dapperware"
ThisBuild / name             := "zio-mongo"

enablePlugins(
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin
)

lazy val core = (project in file("modules/core"))
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "dev.zio"    %% "zio"                            % "2.0.18",
      "dev.zio"    %% "zio-streams"                    % "2.0.18",
      "dev.zio"    %% "zio-bson"                       % "1.0.5",
      "org.mongodb" % "mongodb-driver-reactivestreams" % "4.11.0",
      "dev.zio"    %% "zio-test"                       % "2.0.18" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val root = (project in file("."))
  .aggregate(core)
