# Errors

Parse errors and runtime errors reach Java as exceptions that carry the JavaScript error value and a stack trace with a source excerpt. In the other direction, Java exceptions thrown by host code become JavaScript `Error` objects that scripts can catch, and Java code can raise JavaScript errors of a specific type.

## The exception hierarchy

| Exception | Thrown when | Useful members |
|---|---|---|
| `JSException` | Base class of everything the engine throws. | `getMessage()`, `getSourceNode()` |
| `JSParseException` (extends `JSException`) | The source cannot be parsed (`createScript`, `evaluate*`). | Message with line, column and the offending line. |
| `JSRuntimeException` (extends `JSException`) | A script throws, or the runtime raises a `TypeError`, `ReferenceError`, ... | `getJavascriptException()` (the thrown value), `getStackTraceMessage()`, `getCause()` |
| `JSRuntimeUncatchableException` | Engine-level conditions that scripts must not catch (failed assertions from `UnitTestLibrary`, for example). | |
| `JSRuntimeInterruptException` (extends the above) | The execution was interrupted (`JSAsyncExecutor.stop()`, debugger termination). | |

## Parse errors

Sample: `doc_examples/ErrorsExamples.java` (`testParseErrors`)

```java
try {
    env.createScript("let x = ;", "bad.js");
    fail();
} catch(JSParseException e) {
    // The message embeds the offending source line
    assertTrue(e.getMessage().contains("Encountered ;"));
    assertTrue(e.getMessage().contains("let x = ;"));
}
```

Early errors detected after parsing (for instance a `SyntaxError` from `import.meta` in a script) are reported the same way, wrapped in a `JSParseException` whose cause is the `JSRuntimeException` carrying the `SyntaxError`.

## Runtime errors carry the JavaScript value

`getJavascriptException()` returns the thrown value: a built-in error object (`TypeError`, `RangeError`, ... from `rt/builtins/errors`), or any other value the script threw. The Java message is the error's `toString()` followed by a `----------------- Stack Trace` section listing the script positions with a source excerpt, innermost call last.

Sample: `doc_examples/ErrorsExamples.java` (`testRuntimeErrorsCarryTheJavaScriptValue`, `testThrowingNonErrorValues`)

```java
try {
    env.evaluateScript("function f() { throw new TypeError('bad ' + 1) }\nf()");
    fail();
} catch(JSRuntimeException e) {
    Object jsError = e.getJavascriptException();
    assertTrue(jsError instanceof TypeError);
    assertEquals("TypeError", JSValue.of(env, jsError).get("name").stringValue());
    assertEquals("bad 1", JSValue.of(env, jsError).get("message").stringValue());
    assertTrue(e.getMessage().startsWith("\nTypeError: bad 1"));
    assertTrue(e.getMessage().contains("At script line 2"));   // stack trace, innermost last
}
```

```java
try {
    env.evaluateScript("throw { code: 7 }");
    fail();
} catch(JSRuntimeException e) {
    assertEquals(7, JSValue.of(env, e.getJavascriptException()).get("code").intValue());
}
```

`JSRuntimeException.exceptionObject(Throwable)` is the static helper that extracts the JavaScript value from any throwable (converting a `JSParseException` into a `SyntaxError`), and `asJavascriptException(cause, value)` wraps a value into a `JSRuntimeException`.

## Java exceptions in JavaScript

An exception escaping from host code (a `BaseMethod`, a Java method called through interop) is converted into a JavaScript `Error` whose message is `Java Exception: <message>`; scripts can catch it. When the error is not caught, the `JSRuntimeException` that reaches Java has the original exception as its cause.

To raise a *specific* JavaScript error type from Java, use the factories in `RuntimeUtil`: `error()`, `typeError()`, `rangeError()`, `syntaxError()`, and `referenceError()`. They take a message with `{0}`-style placeholders and return a `JSRuntimeException` to throw.

Sample: `doc_examples/ErrorsExamples.java` (`testJavaExceptionsSurfaceInJavaScript`)

```java
globals.setOwnProperty("boom", new BaseMethod(env, "boom", 0) {
    @Override public Object call(Object t, Object[] a) { throw new IllegalStateException("java failure"); }
});
globals.setOwnProperty("needsNumber", new BaseMethod(env, "needsNumber", 1) {
    @Override public Object call(Object t, Object[] a) {
        if(a.length == 0 || !(a[0] instanceof Number)) {
            throw RuntimeUtil.typeError("needsNumber() expects a number, got {0}", a.length == 0 ? "nothing" : a[0]);
        }
        return ((Number)a[0]).intValue() * 2;
    }
});
```

```java
// An arbitrary Java exception becomes a JavaScript Error
Object r = env.evaluateScript("let r; try { boom() } catch(e) { r = [e instanceof Error, e.name, e.message] } r");
assertEquals(List.of(true, "Error", "Java Exception: java failure"), list(r));

// RuntimeUtil.typeError() (and rangeError, syntaxError, error) produce the matching JavaScript error type
r = env.evaluateScript("let r; try { needsNumber('x') } catch(e) { r = [e instanceof TypeError, e.message] } r");
assertEquals(List.of(true, "needsNumber() expects a number, got x"), list(r));

// Uncaught, the original Java exception is the cause
try {
    env.evaluateScript("boom()");
    fail();
} catch(JSRuntimeException e) {
    assertTrue(e.getCause() instanceof IllegalStateException);
}
```

With the Java interop library, the property `__java_exception__` (constant `Error.JAVA_EXCEPTION`) of the JavaScript error holds the reflection wrapper; its `getCause()` is the exception the Java method threw (`testJavaExceptionObjectIsAvailableInJavaScript`, and [Java Interop](/GaltaJS/UserGuide/JavaInterop)).

## Gotchas

- Compare error types with `instanceof` on the classes in `rt/builtins/errors` (`TypeError`, `RangeError`, ...) or with the `name` property; do not parse the message.
- A `JSRuntimeUncatchableException` bypasses `try`/`catch` in scripts by design.
- The message of a `JSRuntimeException` starts with a newline before the error text, because the stack trace section follows.

## Source

`JSException.java`, `JSParseException.java`, `rt/JSRuntimeException.java`, `rt/JSRuntimeUncatchableException.java`, `rt/JSRuntimeInterruptException.java`, `rt/RuntimeUtil.java` (`error`, `typeError`, `rangeError`, `syntaxError`, `referenceError`), `rt/builtins/errors/`
