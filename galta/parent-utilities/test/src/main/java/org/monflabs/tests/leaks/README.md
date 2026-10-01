# Resource leak detector - design notes

The user documentation is `docs/Utilities/Testing.md` ("Resource leak detection"). This page
describes how the detector works inside.

## What it tracks

A ByteBuddy agent, attached at runtime (`ResourceLeakAgent.install()`), instruments the classes
that hold an OS resource, never their wrappers (a `BufferedInputStream` or a `FileReader` is
tracked through the `FileInputStream` it wraps):

| Flag (`ResourceLeakAgent`) | Default | Classes |
|---|---|---|
| `INSTRUMENT_FILE` | on | `FileInputStream`, `FileOutputStream`, `RandomAccessFile` |
| `INSTRUMENT_PATH` | on | `sun.nio.ch.FileChannelImpl` (constructors) and `AbstractInterruptibleChannel.close()`: the `Files.newInputStream()`, `Files.newByteChannel()`... family |
| `INSTRUMENT_ZIP` | on | `ZipFile` |
| `INSTRUMENT_SOCKET` | off | `Socket`, `ServerSocket`, `DatagramSocket` - not reliable, not tested |
| `INSTRUMENT_JDBC` | off | implementations of `Connection`, `Statement`, `PreparedStatement`, `ResultSet` - not reliable, not tested |

Each constructor records an allocation and each `close()` removes it: whatever is left when the
test ends is a leak, reported with the stack trace of the allocation.

## Components

- **`ResourceLeakAgent`** attaches ByteBuddy's agent (dynamic attach: run the JVM with
  `-XX:+EnableDynamicAgentLoading`, otherwise JDK 21+ prints a warning), adds `BootstrapHelper`
  to the bootstrap class path, wires it to the tracker, then redefines the classes above
  (retransformation, so classes already loaded are covered).
- **`BootstrapHelper`** is the bridge called by the advice inlined into the JDK classes. It must
  be on the bootstrap class path, as the JDK classes cannot see the application class loader. It
  only uses JDK types.
- **`ResourceTracker`** holds the allocations, in a synchronized `IdentityHashMap` with strong
  references (a resource that became garbage without being closed is still a leak), and their
  stack traces. `stopTracking()` releases them.
- **`ResourceLeakRule`** (JUnit 4) and **`__BaseTestCase`** (JUnit 3, when the agent is installed)
  start the tracking before each test and check the leaks after it.

## Wiring the bootstrap copy

`BootstrapHelper` ends up loaded twice: by the application class loader (the agent uses it) and
by the bootstrap class loader (the instrumented JDK classes call it). The bootstrap copy cannot
look the tracker up: `install()` hands the tracker to both copies as JDK-typed callbacks
(`BooleanSupplier` "should this allocation be recorded", `BiConsumer` for the allocations,
`Consumer` for the closures), the bootstrap copy being reached with
`Class.forName(name, true, null)`. `install()` fails with an `IllegalStateException` when the
bootstrap copy is not wired, rather than leaving a detector that silently detects nothing.

`ResourceLeakAgent.setTraceLevel(int)` sets the trace level of both copies (0 silent, the
default; 1 errors and main events; 2 debug). Errors in the advice never break the application:
they are swallowed, and printed only with a trace level of 1 or more.

## Cost when not tracking

The agent stays installed for the life of the JVM. Between tests, an instrumented constructor
or `close()` costs a volatile read and a call: the helper asks `ResourceTracker.shouldRecord()`
first, and only then computes the resource type and the context (file path) and takes the
allocation stack trace.

## Recording rules

- **Thread**: the test base classes track the allocations of the test thread only
  (`startTracking(true)`), so resources opened concurrently by other threads are not reported.
  Closures are recorded whatever the thread.
- **Constructor chaining**: a resource is recorded once, by identity (`FileOutputStream(String)`
  calls `FileOutputStream(File)`).
- **Recursion**: taking the stack trace can load classes, which opens files; a thread-local guard
  ignores the allocations made while recording.
- **Context**: the string, `Path` and `File` constructor arguments, so the report shows the file.

## Filter

`ResourceTracker.shouldReport()`, shared by the rule and `__BaseTestCase.shouldReportLeak()`
(which a test class can override), ignores:

- the resources opened on a jar file: a `jar:` URL connection keeps its jar open in a cache;
- the streams on the standard file descriptors (`new FileOutputStream(FileDescriptor.out)`).

## Known limitations

- Resources closed transitively by a wrapper that does not call `close()` on them, memory-mapped
  files and asynchronous channels are not tracked.
- Sockets and JDBC objects are not tracked by default (see the flags above).
- The tracker is per JVM: tests running in parallel in one JVM can only rely on the
  current-thread filter.
