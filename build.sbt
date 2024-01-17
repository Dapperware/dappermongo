ThisBuild / scalaVersion               := "2.13.12"
ThisBuild / organization               := "com.github.dapperware"
ThisBuild / organizationName           := "Dapperware"
ThisBuild / name                       := "dappermongo"
ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbVersion          := scalafixSemanticdb.revision
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)

inThisBuild(
  List(
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/dapperware/dappermongo/"),
        "scm:git:git@github.com:dapperware/dappermongo.git"
      )
    ),
    developers := List(
      Developer(
        "paulpdaniels",
        "Paul Daniels",
        "",
        url("https://github.com/paulpdaniels")
      )
    ),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    )
  )
)

enablePlugins(
  ZioSbtEcosystemPlugin
)

def scalacOptionsVersion(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
  case Some((2, 13)) => Seq("-Wunused")
  case Some((2, 12)) => Seq("-Xlint:unused")
  case _             => Nil
}

lazy val core = (project in file("modules/core"))
  .settings(
    name := "dappermongo-core",
    libraryDependencies ++= Seq(
      "dev.zio"           %% "zio"                            % "2.0.21",
      "dev.zio"           %% "zio-streams"                    % "2.0.21",
      "dev.zio"           %% "zio-interop-reactivestreams"    % "2.0.2",
      "org.mongodb"        % "mongodb-driver-reactivestreams" % "4.11.0",
      "org.reactivemongo" %% "reactivemongo-bson-msb-compat"  % "1.1.0-RC12",
      "dev.zio"           %% "zio-test"                       % "2.0.21"  % Test,
      "dev.zio"           %% "zio-test-sbt"                   % "2.0.21"  % Test,
      "com.dimafeng"      %% "testcontainers-scala-mongodb"   % "0.40.12" % Test
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
