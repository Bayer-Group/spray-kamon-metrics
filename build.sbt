name := "spray-kamon-metrics"

homepage := Some(url("https://monsantoco.github.io/spray-kamon-metrics"))

organization := "com.monsanto.arch"

organizationHomepage := Some(url("http://engineering.monsanto.com/"))

startYear := Some(2015)

description := "Better Kamon metrics for Spray services"

licenses += "BSD" → url("http://opensource.org/licenses/BSD-3-Clause")

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked"
)

val akkaVersion = "2.4.16"
val kamonVersion = "0.6.5"
val sprayVersion = "1.3.4"

libraryDependencies ++= Seq(
  "com.typesafe"        % "config"           % "1.3.1",
  "io.kamon"           %% "kamon-core"       % kamonVersion,
  "io.spray"           %% "spray-can"        % sprayVersion  % "provided",
  "io.spray"           %% "spray-routing"    % sprayVersion  % "provided",
  "org.scalatest"      %% "scalatest"        % "3.0.1"       % "test",
  "ch.qos.logback"      % "logback-classic"  % "1.1.8"       % "test",
  "com.typesafe.akka"  %% "akka-slf4j"       % akkaVersion   % "test",
  "com.typesafe.akka"  %% "akka-testkit"     % akkaVersion   % "test"
)

dependencyOverrides := Set(
  "com.typesafe"        % "config"           % "1.3.1"
)

fork in Test := true

javaOptions in Test += s"-DTARGET_DIR=${(target in Test).value.absolutePath}"

// We have to ensure that Kamon starts/stops serially
parallelExecution in Test := false

bintrayOrganization := Some("monsanto")

bintrayPackageLabels := Seq("kamon", "spray", "metrics")

bintrayVcsUrl := Some("https://github.com/MonsantoCo/spray-kamon-metrics")

ghpages.settings

git.remoteRepo := "git@github.com:MonsantoCo/spray-kamon-metrics.git"

apiMappingsScala := Map(
  ("com.typesafe.akka", "akka-actor") → "http://doc.akka.io/api/akka/%s"
)

apiMappingsJava := Map(
  ("com.typesafe", "config") → "http://typesafehub.github.io/config/latest/api"
)

apiURL := Some(url(s"https://monsantoco.github.io/spray-kamon-metrics/api/${(version in ThisBuild).value}"))

enablePlugins(AsciidoctorPlugin)

enablePlugins(SiteScaladocPlugin)

siteSubdirName in SiteScaladoc := "api/snapshot"
