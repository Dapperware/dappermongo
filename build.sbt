ThisBuild / scalaVersion               := "2.13.12"
ThisBuild / version                    := "0.1.0-SNAPSHOT"
ThisBuild / organization               := "com.github.dapperware"
ThisBuild / organizationName           := "Dapperware"
ThisBuild / name                       := "zio-mongo"
ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbVersion          := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)

enablePlugins(
  ZioSbtEcosystemPlugin
)

def scalacOptionsVersion(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
  case Some((2, 13)) => Seq("-Wunused")
  case _             => Nil
}

lazy val core = (project in file("modules/core"))
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "dev.zio"      %% "zio"                            % "2.0.18",
      "dev.zio"      %% "zio-streams"                    % "2.0.18",
      "dev.zio"      %% "zio-interop-reactivestreams"    % "2.0.2",
      "dev.zio"      %% "zio-bson"                       % "1.0.5",
      "org.mongodb"   % "mongodb-driver-reactivestreams" % "4.11.0",
      "dev.zio"      %% "zio-schema-bson"                % "0.4.15"  % Test,
      "dev.zio"      %% "zio-schema-derivation"          % "0.4.15"  % Test,
      "dev.zio"      %% "zio-test"                       % "2.0.18"  % Test,
      "dev.zio"      %% "zio-test-sbt"                   % "2.0.18"  % Test,
      "com.dimafeng" %% "testcontainers-scala-mongodb"   % "0.40.12" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / fork   := true,
    scalacOptions := scalacOptionsVersion(scalaVersion.value)
  )

//lazy val docs = project
//  .in(file("zio-mongo-docs"))
//  .settings(
//    scalacOptions --= List("-Yno-imports", "-Xfatal-warnings"),
//    publish / skip := true
//  )
//  .settings(
//    moduleName                                 := "zio-mongo-docs",
//    projectName                                := (ThisBuild / name).value,
//    mainModuleName                             := (core / moduleName).value,
//    projectStage                               := ProjectStage.Development,
//    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core)
//  )
//  .dependsOn(core)
//  .enablePlugins(WebsitePlugin)

lazy val root = (project in file("."))
  .aggregate(core)
  .settings(
    publish / skip := true
  )
