# GaltaJS Debugger

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/js-debugger?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/js-debugger)

A Swing debugger for GaltaJS, laid out like the Sources panel of Chrome
DevTools: a script navigator, a source view with breakpoints and the execution
pointer, the call stack, watches, scopes, a breakpoint list and a console. It
speaks the Chrome DevTools Protocol (CDP), either over a WebSocket - so it can
attach to any engine publishing a `ws://` debugger URL - or in process, with no
socket, against the CDP server built into the [`js`](../js/README.md) engine.
The [GaltaJS playground](../js-playground/README.md) embeds the same panel.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-debugger</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare `<version>` directly.

The quickest way to step through a script is `SwingDebugger`: construct it,
`setVisible(true)`, then call `debugScript(unit, contextFactory)` with a compiled
`JSInterpretedUnit` and a supplier of the global context to run it in; the
script starts paused at its first statement. The script must come from an
environment built with `debug(true)`, otherwise it has no debug hooks and
breakpoints never resolve. To embed the debugger in another
window, add a `DebuggerPanel` and `attach(wsUrl)` it to a CDP endpoint.

## Contents

Package `org.monflabs.js.debugger.ui`:

- `SwingDebugger` - a standalone window that debugs a script in process
- `DebuggerPanel` - the embeddable debugger component (`attach`, `detach`, `close`, connection-state listener)
- `model/` - `DebugSession`, the debugging state and commands (resume, step, breakpoints with conditions, pause on exceptions, watches, evaluation) independent of Swing, and its value types (`CallFrame`, `Scope`, `RemoteValue`, `Breakpoint`, ...)
- `panel/` - the individual views: source, script navigator, call stack, scopes, watches, breakpoints, console
- `cdp/` - the CDP client: `CdpConnection` over a JDK WebSocket (`JdkWebSocketChannel`)

## Documentation

- [Debugging](../../../docs/GaltaJS/UserGuide/Debugging.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/Debugging)) - debug mode, the CDP server, attaching a client
- [Debugger architecture](../../../docs/GaltaJS/Architecture/Debugger.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Debugger))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
