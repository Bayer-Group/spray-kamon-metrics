name := "spray-kamon-metrics"

organization := "com.monsanto.arch"

startYear := Some(2015)

description := "Better Kamon metrics for Spray services"

licenses += "BSD" → url("http://opensource.org/licenses/BSD-3-Clause")

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked"
)

val akkaVersion = "2.3.14"
val kamonVersion = "0.5.1"
val sprayVersion = "1.3.2"

libraryDependencies ++= Seq(
  "com.typesafe"        % "config"           % "1.3.0",
  "io.kamon"           %% "kamon-core"       % kamonVersion,
  "io.spray"           %% "spray-can"        % sprayVersion  % "provided",
  "io.spray"           %% "spray-routing"    % sprayVersion  % "provided",
  "org.scalatest"      %% "scalatest"        % "2.2.4"       % "test",
  "ch.qos.logback"      % "logback-classic"  % "1.1.3"       % "test",
  "com.typesafe.akka"  %% "akka-slf4j"       % akkaVersion   % "test",
  "com.typesafe.akka"  %% "akka-testkit"     % akkaVersion   % "test"
)

dependencyOverrides := Set(
  "org.scala-lang"          % "scala-library" % scalaVersion.value,
  "org.scala-lang"          % "scala-reflect" % scalaVersion.value,
  "org.scala-lang.modules" %% "scala-xml"     % "1.0.4",
  "com.typesafe.akka"      %% "akka-actor"    % akkaVersion
)

fork in Test := true

javaOptions in Test += s"-DTARGET_DIR=${(target in Test).value.absolutePath}"

// We have to ensure that Kamon starts/stops serially
parallelExecution in Test := false

bintrayOrganization := Some("monsanto")

bintrayPackageLabels := Seq("kamon", "spray", "metrics")

bintrayVcsUrl := Some("https://github.com/MonsantoCo/spray-kamon-metrics")
