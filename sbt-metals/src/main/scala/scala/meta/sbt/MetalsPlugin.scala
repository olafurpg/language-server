package scala.meta.sbt {

  import sbt._
  import sbt.Keys._
  import java.io._

  object MetalsPlugin extends AutoPlugin {
    override def trigger = allRequirements
    override def requires = sbt.plugins.JvmPlugin

    def semanticdbVersion = System.getProperty("scalameta.version", "4.0.0")

    override def globalSettings = Seq(
      commands ++= Seq(
        semanticdbEnable
      )
    )

    private def isValidScalaBinaryVersion: Set[String] = Set("2.11", "2.12")

    /** Command to automatically enable semanticdb-scalac for shell session */
    lazy val semanticdbEnable = Command.command(
      "semanticdbEnable",
      briefHelp =
        "Configure libraryDependencies, scalaVersion and scalacOptions for scalafix.",
      detail = """1. enables the semanticdb-scalac compiler plugin
                 |2. add -Yrangepos to scalacOptions""".stripMargin
    ) { s =>
      val extracted = Project.extract(s)
      val settings: Seq[Setting[_]] = for {
        p <- extracted.structure.allProjectRefs
        if scalaBinaryVersion
          .in(p)
          .get(extracted.structure.data)
          .exists(isValidScalaBinaryVersion)
        isDisabled = {
          val metalsEnabled =
            SettingKey[Boolean]("metalsEnabled")
              .in(p)
              .get(extracted.structure.data)
          metalsEnabled == Some(false)
        }
        if !isDisabled
        setting <- List(
          scalacOptions.in(p) --= List(
            // Disable fatal warnings so that SemanticDBs are generated even for unused warnings.
            // Down the road, metals can even remove unused imports for you if they are reported
            // as warnings but not if they are errors.
            "-Xfatal-warning"
          ),
          scalacOptions.in(p) ++= List(
            // Don't fail compilation in case of Scalameta crash during SemanticDB generation.
            s"-P:semanticdb:failures:warning",
            // The bloop server runs from a different working directory than the scala compiler
            // from inside an sbt shell session, setting sourceroot ensures paths are
            // relativized by the base directory of the build regardless.
            s"-P:semanticdb:sourceroot:${baseDirectory.in(ThisBuild).value}",
            "-Yrangepos",
            s"-Xplugin-require:semanticdb"
          ),
          libraryDependencies.in(p) ++= {
            List(
              compilerPlugin(
                "org.scalameta" % "semanticdb-scalac" %
                  semanticdbVersion cross CrossVersion.full
              )
            )
          }
        )
      } yield setting
      val semanticdbInstalled = Compat.appendWithSession(extracted, settings, s)
      s.log.info("semanticdb-scalac is enabled")
      semanticdbInstalled
    }
  }
}

package sbt {
  package internal {
    // This package doesn't exist in sbt 0.13
  }

  // We need `Load`, which is `sbt.Load` in 0.13, `sbt.internal.Load` in 1.0.
  import internal._
  object Compat {

    // Copy-pasted `appendWithSession` from sbt, available only in sbt 1.1.
    def appendWithSession(
        extracted: Extracted,
        settings: Seq[Setting[_]],
        state: State
    ): State =
      appendImpl(extracted, settings, state, extracted.session.mergeSettings)

    private[this] def appendImpl(
        extracted: Extracted,
        settings: Seq[Setting[_]],
        state: State,
        sessionSettings: Seq[Setting[_]]
    ): State = {
      import extracted.{currentRef, rootProject, session, showKey, structure}
      val appendSettings =
        Load.transformSettings(
          Load.projectScope(currentRef),
          currentRef.build,
          rootProject,
          settings
        )
      val newStructure =
        Load.reapply(sessionSettings ++ appendSettings, structure)
      Project.setProject(session, newStructure, state)
    }
  }
}
