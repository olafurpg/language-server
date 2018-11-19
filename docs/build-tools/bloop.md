---
id: bloop
title: Bloop
---

[Bloop](https://scalacenter.github.io/bloop/docs/installation/#sbt) is a compile
server for Scala that works with sbt and has experimental support for other
build tools like Maven, Gradle and Mill. If your workspace contains a `.bloop/`
directory with Bloop JSON files then Metals will automatically connect to it.

To manually tell Metals to connect with Bloop, run the "Connect to build server"
(`build.connect`) command. In VS Code, open the the "Command palette"
(`Cmd + Shift + P`) and search "connect to build server".

![Import connect to build server command](assets/vscode-connect-build-server.png)

Metals works with Bloop v1.1.0+.

- If you have a compatible version of Bloop installed on your machine then
  Metals will connect to that server instance.
- If you don't have Bloop installed on your machine or the installed version is
  outdated then Metals will start its own Bloop server instance that shuts down
  when Metals exits.
