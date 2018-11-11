---
id: new-editor
title: Integrating a new editor
---

Metals is a language server implemented in Scala that communicates with a single
client over [JSON-RPC](https://www.jsonrpc.org/specification).

## Starting the server

Metals requires Java 8, see
[scalameta/scalameta#1779](https://github.com/scalameta/scalameta/issues/1779)
for the progress on adding support for Java 11. Use
[Coursier](https://github.com/coursier/coursier) to obtain the JVM classpath of
Metals:

```sh
java -classpath $(coursier fetch org.scalameta:metals_2.12:$VERSION -p) scala.meta.metals.Main
```

It is recommended to enable JVM string de-duplication and provide generous stack
size and memory options.

```sh
java -XX:+UseG1GC -XX:+UseStringDeduplication -Xss4m -Xms1G -Xmx4G ...
```

JSON-RPC communication takes place over standard input/output so the Metals
server does not print anything to the console when it starts. Instead, before
establishing a connection with the client, Metals logs notifications to a global
directory:

```sh
# macOS
~/Library/Caches/org.scalameta.metals/global.log
# Linux
$XDG_CACHE_HOME/org.scalameta.metals/global.log
# Linux (alternative)
$HOME/.cache/org.scalameta.metals/global.log
# Windows
{FOLDERID_LocalApplicationData}\.cache\org.scalameta.metals\global.log
```

After establishing a connection with the client, Metals redirects logs to the
`.metals/metals.log` file in the LSP workspace root directory.

Metals supports two kinds of JSON-RPC endpoints:

- [Language Server Protocol](#language-server-protocol): for the main
  functionality of the server, including editor text synchronization and
  semantic features such as goto definition.
- [Metals extensions](#metals-extensions): for additional functionality that is
  missing in LSP but improves the user experience.

## Language Server Protocol

Consult the
[LSP specification](https://microsoft.github.io/language-server-protocol/specification)
to learn more more how LSP works. Metals uses the following endpoints from the
specification.

### `initialize`

- the `rootUri` field is used to configure Metals for that workspace directory.
  The working directory for where server is started has no significant meaning.
- at this point, Metals uses only full text synchronization. In the future, it
  will be able to use incremental text synchronization.
- `didChangeWatchedFiles` client capability is used to determine whether to
  register file watchers.

### `initialized`

Triggers build server initialization and workspace indexing.

### `shutdown`

Triggers build server shutdown.

### `exit`

Kills the process using `System.exit`.

### `$/cancelRequest`

Used by `metals/slowTask` to notify when a long-running process has finished.

### `client/registerCapability`

If the client declares the `workspace.didChangeWatchedFiles` capability during
the `initialize` request, then Metals follows up with a
`client/registerCapability` request to register file watchers for certain glob
patterns.

### `textDocument/didOpen`

Triggers compilation in the build server for the build target containing the
opened document. Related, see `metals/didFocus`.

### `textDocument/didChange`

Required to know the text contents of the current unsaved buffer.

### `textDocument/didClose`

Cleans up resources.

### `textDocument/didSave`

Triggers compilation in the build server and analyses if the build needs to be
re-imported.

### `textDocument/publishDiagnostics`

Metals forwards diagnostics from the build server to the editor client.
Additionally, Metals publishes `Information` diagnostics for unexpected
compilation errors when navigating external library sources.

### `textDocument/definition`

Metals supports goto definition for workspace sources in addition to external
library sources.

- Library sources live under the directory `.metals/readonly` and they are
  marked as read-only to prevent the user from editing them.
- The destination location can either be a Scala or Java source file. It is
  recommended to have a Java language server installed to navigate Java sources.

### `workspace/didChangeWatchedFiles`

Same as `didSave`, triggers compilation and analyses if the build needs to be
re-imported. File watching notifications are optional, the Metals server should
function normally without file watching notifications. However, file watching
notifications improve the user experience especially when file contents change
outside of the editor such as during `git checkout`.

### `window/logMessage`

Used to log non-critical and non-actionable information. The user is only
expected to use the logs for troubleshooting or finding metrics for how long
certain events take.

### `window/showMessage`

Used to send critical but non-actionable notifications to the user. For
non-critical notifications, see `metals/status`.

### `window/showMessageRequest`

Used to send critical and actionable notifications to the user. To notify the
user about long running tasks that can be cancelled, the extension
`metals/slowTask` is used instead.

## Metals extensions

Editor clients can opt into receiving Metals-specific JSON-RPC requests and
notifications. Metals extensions are not defined in LSP and are not strictly
required for the Metals server to function but it is recommended to implement
them to improve the user experience.

To enable Metals extensions, start the main process with the system property
`-Dmetals.extensions=true`.

### `metals/slowTask`

The Metals slow task request is sent from the server to the client to notify the
start of a long running process with unkwown estimated total time. A
`cancel: true` response from the client cancels the task. A `$/cancelRequest`
request from the server indicates that the task has completed.

![Metals slow task](assets/metals-slow-task.gif)

The difference between `metals/slowTask` and `window/showMessageRequest` is that
`slowTask` is time-sensitive and the interface should display a timer for how
long the task has been running while `showMessageRequest` is static.

### `metals/status`

The Metals status notification is sent from the server to the client to notify
about non-critical and non-actionable events that are happening in the server.
Metals status notifications are a complement to `window/showMessage` and
`window/logMessage`. Unlike `window/logMessage`, status notifications should
always be visible in the user interface. Unlike `window/showMessage`, status
notifications are not critical meaning that they should not demand too much
attention from the user.

In general, Metals uses status notifications to update the user about ongoing
events in the server such as batch compilation in the build server or when a
successful connection was established with the build server.

![Metals status bar](assets/metals-status.gif)

The "🚀 Imported build" and "🔄 Compiling explorer" messages at the bottom of
the window are `metals/status` notifications.

### `metals/didFocus`

The Metals did focus notification is sent from the client to the server when the
editor changes focus to a new text document. Unlike `textDocument/didOpen`, the
did focus notification is sent even when the text document is already open.

![Metals did focus](assets/metals-did-focus.gif)

Observe that the compilation error appears as soon as `UserTest.scala` is
focused even if the text document was already open before. The LSP
`textDocument/didOpen` notitication is only sent the first time a document so it
is not possible for the language server to re-trigger compilation when moves
focus back to `UserTest.scala` that depends on APIs defined in `User.scala`.
