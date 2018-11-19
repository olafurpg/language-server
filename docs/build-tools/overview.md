---
id: overview
title: Build Tools Overview
sidebar_label: Overview
---

Metals works with a limited set of builds tools. Some build tools require manual
installation while others can be automatically installed.

| Build tool | Automatic installation | Manual installation |
| ---------- | :--------------------: | :-----------------: |
| sbt        |          Yes           |         Yes         |
| Bloop      |          Yes           |         Yes         |
| Maven      |           No           |    Experimental     |
| Gradle     |           No           |    Experimental     |
| Mill       |           No           |    Experimental     |
| Pants      |           No           |         No          |
| Bazel      |           No           |         No          |

## Automatic installation

Automatic installation means that you can import the build directly from the
language server without the need for running custom steps in the terminal. To
use automatic installation start the Metals language server in the root
directory of your build.

## Manual installation

Manual build installation is done via
[Bloop](https://scalacenter.github.io/bloop), a compile server for Scala. In
addition to normal Bloop installation, Metals requires that the project sources
are compiled with the
[semanticdb-scalac](https://scalameta.org/docs/semanticdb/guide.html#producing-semanticdb)
compiler plugin and `-Yrangepos` option enabled.
