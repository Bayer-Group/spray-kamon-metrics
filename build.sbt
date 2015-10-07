name := "spray-kamon-metrics"

homepage := Some(url("https://monsantoco.github.io/spray-kamon-metrics"))

organization := "com.monsanto.arch"

organizationHomepage := Some(url("http://engineering.monsanto.com/"))

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
val sprayVersion = "1.3.3"

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

site.settings

ghpages.settings

git.remoteRepo := "git@github.com:MonsantoCo/spray-kamon-metrics.git"

site.includeScaladoc("api/latest")

autoAPIMappings := true

val scalaApiMappings = Map(
  ("com.typesafe.akka", "akka-actor") → "http://doc.akka.io/api/akka/%s"
)

val javaApiMappings = Map(
  ("com.typesafe", "config") → "http://typesafehub.github.io/config/latest/api"
)

val rtJar = System.getProperty("sun.boot.class.path").split(java.io.File.pathSeparator).collectFirst {
  case str: String if str.endsWith(java.io.File.separator + "rt.jar") => file(str)
}.get

val javaApiLocation = "http://docs.oracle.com/javase/8/docs/api"

apiMappings ++= {
  val dependencyMap =
    (
      for {
        entry ← (fullClasspath in Compile).value
        maybeModule = entry.get(moduleID.key)
        if maybeModule.isDefined
        module ← maybeModule
        name = module.name.replaceAll(s"_${scalaBinaryVersion.value}$$", "")
      } yield (module.organization, name) → (entry.data, module.revision)
    ).toMap
  def makeApiMapping(orgName: (String,String), urlFormat: String): (File, URL) = {
    dependencyMap.get(orgName).map { data =>
      data._1 → url(urlFormat.format(data._2))
    }.get
  }

  (javaApiMappings ++ scalaApiMappings).map {
    case (orgName, urlFormat) =>
      makeApiMapping(orgName, urlFormat)
  } + (rtJar → url(javaApiLocation))
}

val javadocURLs: Seq[String] = javaApiMappings.values.toSeq :+ javaApiLocation

def javadocURLRegex(url: String) = ("""\"(\Q""" + url + """\E)/index.html#([^"]*)\"""").r

def hasJavadocLink(f: File): Boolean = javadocURLs.exists { javadocURL ⇒
  javadocURLRegex(javadocURL).findFirstIn(IO.read(f)).nonEmpty
}

val fixJavaLinks: scala.util.matching.Regex.Match ⇒ String = m ⇒ m.group(1) + "/" + m.group(2).replace(".", "/") + ".html"

doc in Compile <<= (doc in Compile) map { target ⇒
  (target ** "*.html").get.filter(hasJavadocLink).foreach { f ⇒
    val newContent = javadocURLs.foldLeft(IO.read(f)) {
      case (oldContent, javadocURL) ⇒
        javadocURLRegex(javadocURL).replaceAllIn(oldContent, fixJavaLinks)
    }
    IO.write(f, newContent)
  }
  target
}

apiURL := Some(url(s"https://monsantoco.github.io/spray-kamon-metrics/api/${(version in ThisBuild).value}"))

site.asciidoctorSupport()
