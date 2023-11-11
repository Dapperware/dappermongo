ThisBuild / scalaVersion               := "2.13.12"
ThisBuild / version                    := "0.1.0-SNAPSHOT"
ThisBuild / organization               := "com.github.dapperware"
ThisBuild / organizationName           := "DapperWare"
ThisBuild / name                       := "zio-mongo"
ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbVersion          := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)

enablePlugins(
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin
)

lazy val core = (project in file("modules/core"))
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "dev.zio"      %% "zio"                            % "2.0.18",
      "dev.zio"      %% "zio-streams"                    % "2.0.18",
      "dev.zio"      %% "zio-interop-reactivestreams"    % "2.0.2",
      "dev.zio"      %% "zio-bson"                       % "1.0.5",
      "dev.zio"      %% "zio-bson-magnolia"              % "1.0.5"   % Test,
      "org.mongodb"   % "mongodb-driver-reactivestreams" % "4.11.0",
      "dev.zio"      %% "zio-test"                       % "2.0.18"  % Test,
      "dev.zio"      %% "zio-test-sbt"                   % "2.0.18"  % Test,
      "com.dimafeng" %% "testcontainers-scala-mongodb"   % "0.40.12" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / fork := true,
    scalacOptions ++= Seq(
      "-Wunused"
    )
  )

lazy val root = (project in file("."))
  .aggregate(core)
