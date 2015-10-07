package apimappings

import java.io.{File ⇒ JFile}
import sbt._
import sbt.Keys._

import scala.util.matching.Regex

object ApiMappingsPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  object autoImport {
    lazy val apiMappingsJava = settingKey[Map[(String,String), String]]("Java API mappings")
    lazy val apiMappingsScala = settingKey[Map[(String,String), String]]("Scala API mappings")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    autoAPIMappings := true,
    apiMappingsJava := Map.empty,
    apiMappingsScala := Map.empty,
    apiMappings := {
      val dependencyMap = {
        for {
          entry ← (fullClasspath in Compile).value
          maybeModule = entry.get(moduleID.key)
          if maybeModule.isDefined
          module ← maybeModule
          name = module.name.replaceAll(s"_${scalaBinaryVersion.value}$$", "")
        } yield (module.organization, name) → (entry.data, module.revision)
      }.toMap
      val findMapping: PartialFunction[((String,String),String), (File,URL)] = {
        case (orgName, urlFormat) if dependencyMap.contains(orgName) ⇒
          dependencyMap.get(orgName).map { data ⇒
            data._1 → url(urlFormat.format(data._2))
          }.get
      }
      (apiMappingsJava.value ++ apiMappingsScala.value).collect(findMapping) + (RtJar → url(JavaApiLocation))
    },
    (doc in Compile) := {
      val docDir = (doc in Compile).value
      val javadocURLs = apiMappingsJava.value.values.toSeq :+ JavaApiLocation
      def hasJavadocLink(f: File): Boolean = javadocURLs.exists { javadocURL ⇒
        javadocURLRegex(javadocURL).findFirstIn(IO.read(f)).nonEmpty
      }
      (docDir ** "*.html").get.filter(hasJavadocLink).foreach { f ⇒
        val newContent = javadocURLs.foldLeft(IO.read(f)) {
          case (oldContent, javadocURL) ⇒
            javadocURLRegex(javadocURL).replaceAllIn(oldContent, fixJavaLinks)
        }
        IO.write(f, newContent)
      }
      docDir
    }
  )

  private lazy val RtJar = System.getProperty("sun.boot.class.path").split(JFile.pathSeparator).collectFirst {
    case str: String if str.endsWith(JFile.separator + "rt.jar") ⇒ file(str)
  }.get

  private val JavaApiLocation = "http://docs.oracle.com/javase/8/docs/api"

  private def javadocURLRegex(url: String) = ("""\"(\Q""" + url + """\E)/index.html#([^"]*)\"""").r
  private val fixJavaLinks: Regex.Match ⇒ String = m ⇒ m.group(1) + "/" + m.group(2).replace(".", "/") + ".html"
}
