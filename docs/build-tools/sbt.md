---
id: sbt
title: sbt
---

## Automatic installation

Metals can automatically import sbt builds for v0.13.16+ and v1.0.0+. Importing
an sbt build involves generating [Bloop](https://scalacenter.github.io/bloop/)
JSON files describing project library dependencies and Scala compiler options.

The first time you open a directory containing an sbt build you are prompted to
import the build via Bloop.

![Import build via Bloop](assets/import-via-bloop.png)

Click "Import build via Bloop" to start the `sbt bloopInstall` import step.

![sbt bloopInstall](assets/sbt-bloopinstall.png)

This step can take a long time, especially the first time you run it in a new
workspace. The exact time depends on the complexity of the build and if library
dependencies are cached or need to be downloaded. For example, this step can
take everything from 10 seconds in small cached builds up to 10-15 minutes in
large uncached builds.

Once the import step completes, compilation starts for your open files. Once
compilation completes you can use all the functionality of Metals.

When you change `build.sbt` or sources under `project/`, you will be prompted to
re-import the build.

![Import sbt changes](assets/sbt-import-changes.png)

Click "Import changes" and that will restart the `sbt bloopInstall` step.

To manually trigger a build import, execute the "Import build" ( `build.import`)
command. In VS Code, open the "Command palette" (`Cmd + Shift + P`) and search
"import build".

![Import build command](assets/vscode-import-build.png)

## Manual installation

Manual build installation is done via
[Bloop](https://scalacenter.github.io/bloop), a compile server for Scala. In
addition to normal Bloop installation, Metals requires that the project sources
are compiled with the
[semanticdb-scalac](https://scalameta.org/docs/semanticdb/guide.html#producing-semanticdb)
compiler plugin and `-Yrangepos` option enabled.

First, install the Bloop and Metals plugins globally

```scala
// One of:
//   ~/.sbt/0.13/plugins/plugins.sbt
//   ~/.sbt/1.0/plugins/plugins.sbt
addSbtPlugin("org.scalameta" % "sbt-metals" % "@VERSION@")
addSbtPlugin("org.scalameta" % "sbt-bloop" % "@BLOOP_VERSION@")
```

Next, run `sbt metalsEnable bloopInstall` to generate the Bloop JSON
configuration files.

Finally, once `bloopInstall` is finished, execute the
"[Connect to build server](bloop.md)" command to tell Metals to communicate with
the Bloop build server.

For more information about sbt-bloop, consult the
[Bloop website](https://scalacenter.github.io/bloop/docs/installation/#sbt).

### Permanent metalsEnable

To avoid running the `metalsEnable` command in every sbt session you can
permanently enable it with the following steps.

First, update the global Bloop settings to download sources of external
libraries.

```scala
// One of:
//   ~/.sbt/0.13/build.sbt
//   ~/.sbt/1.0/build.sbt
bloopExportJarClassifiers.in(Global) := Some(Set("sources"))
```

Next, update your build settings to use the
[semanticdb-scalac](https://scalameta.org/docs/semanticdb/guide.html) compiler
plugin.

```diff
// build.sbt
 lazy val myproject = project.settings(
+  scalaVersion := "@SCALA_VERSION@", // or @SCALA211_VERSION@, other versions are not supported.
+  addCompilerPlugin(MetalsPlugin.semanticdbModule), // enable SemanticDB
+  scalacOptions += "-Yrangepos" // required by SemanticDB
 )
```

Now, you can run `sbt bloopInstall` without the `metalsEnable` step.

**Pro tip**: With semanticdb-scalac enabled in your sbt build can also use
[Scalafix](https://scalacenter.github.io/scalafix).
