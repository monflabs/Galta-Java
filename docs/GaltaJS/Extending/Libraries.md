# Libraries

A library is a Java object that contributes to a `JSEnvironment`: global objects and functions, configuration flags, module resolvers, and accessors for Java classes. Libraries are registered on the builder with `registerLibrary()` and are the mechanism behind everything the engine ships as "standard" (`Math`, `JSON`, `console`... all come from `StandardLibrary`).

## The SPI

`org.monflabs.galtajs.JSLibrary` has three methods:

```java
public interface JSLibrary {
	public void configureEnvironment(Builder builder);
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects);
	public JSAccessor createAccessor(JSEnvironment env, Class<?> clazz);
}
```

| Method | Called | Purpose |
|---|---|---|
| `configureEnvironment(Builder)` | Once per builder, inside `build()`, before the environment is created | Flip configuration flags, add module resolvers (`builder.addModuleResolver(...)`), register other libraries |
| `configureStandardObjects(env, standardObjects)` | Once per environment, after every built-in constructor and prototype is installed | Add globals with `standardObjects.setOwnProperty(name, value)` or `setOwnMethod(BaseMethod)` |
| `createAccessor(env, clazz)` | Lazily, the first time a value of class `clazz` reaches the engine and no built-in accessor matches | Return a `JSAccessor` for the class, or `null` to let the next library answer (see [Accessors](/GaltaJS/Extending/Accessors)) |

Base classes save you from implementing all three:

- `AbstractLibrary` implements the three methods as no-ops (`createAccessor` returns `null`).
- `library/GlobalLibrary` extends `AbstractLibrary`; it is the conventional base class for libraries that contribute globals. Instances must be stateless with respect to the environment, because one library instance can configure several environments.
- `library/StaticLibrary` is a ready-made `GlobalLibrary` holding a map of name to value.

## Registration and ordering

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder()
        .registerLibrary(new GreetingLibrary())
        .build();
```

`registerLibrary` appends to a `CustomLibraries` list. Libraries are configured in registration order, and a global set by a later library overwrites the same name set earlier:

```java
StaticLibrary first = new StaticLibrary();
first.addStaticGlobal("who", "first");
StaticLibrary second = new StaticLibrary();
second.addStaticGlobal("who", "second");
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(first).registerLibrary(second).build();
assertEquals("second", env.evaluateExpression("who"));   // last registration wins
```

Sample: `doc_examples/LibrariesExamples.java` (`testLibrariesAreRegisteredInOrder`)

`JavaLibrary` is special-cased: at most one may be registered (`IllegalStateException` otherwise) and it always runs last in all three phases, so reflection is the fallback after every other accessor.

The prebuilt environments register very little: `JavaScriptEnvironment` registers `StandardLibrary` only, `GaltaJSEnvironment` adds `JavaLibrary`. Timers, `fetch`, `require` and `fs` each need their own library, see [Bundled libraries](/GaltaJS/Extending/BundledLibraries).

## A complete library

Sample: `doc_examples/LibrariesExamples.java` (`GreetingLibrary`, `testCustomLibrary`)

```java
public static class GreetingLibrary extends GlobalLibrary {

	@Override
	public void configureEnvironment(Builder builder) {
		// Runs once per builder, before the environment exists: flags and module resolvers go here
		builder.supportGlobalAlias(true);
		builder.addModuleResolver(new JSMemoryModuleResolver()
				.put("greeting/format", "export const exclaim = s => s + '!';"));
	}

	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects globals) {
		// Runs once per environment, after all built-ins are installed
		globals.setOwnProperty("greet", new BaseMethod(env, "greet", 1) {
			@Override
			public Object call(Object thisValue, Object[] args) {
				String who = args.length > 0 ? RuntimeUtil.toString(env, args[0]) : "world";
				return "Hello " + who;
			}
		});
		globals.setOwnProperty("greeting", JSObject.of(env, "version", 2, "languages", List.of("en", "fr")));
	}
}
```

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new GreetingLibrary()).build();
assertEquals("Hello Ada", env.evaluateExpression("greet('Ada')"));
assertEquals("Hello world", env.evaluateExpression("greet()"));
assertEquals(2, env.evaluateExpression("greeting.version"));
assertEquals("object", env.evaluateExpression("typeof global"));
assertEquals("Hello Ada!", env.evaluateScript("import { exclaim } from 'greeting/format'; exclaim(greet('Ada'))"));
```

## Global functions: `BaseMethod`

A JavaScript-callable Java function extends `rt/builtins/BaseMethod`:

```java
public abstract class BaseMethod extends BaseNativeMethod {
	protected BaseMethod(JSEnvironment env, Object id, int length);   // id: String or Symbol
	public final Object getId();
	public abstract Object call(Object thisValue, Object[] args);
}
```

The constructor installs the spec-shaped own properties `name` (from `id`) and `length` (the declared arity), both read-only and configurable, and deliberately no `prototype` property (a plain built-in method is not a constructor). `args` is never `null`; missing arguments are simply absent, so check `args.length` before reading. Return any JavaScript value: a Java primitive wrapper, `String`, `JSObject`, `JSArray`, `RuntimeUtil.UNDEFINED`, or `null` for `null`.

```java
globals.setOwnProperty("shout", new BaseMethod(env, "shout", 1) {
	@Override
	public Object call(Object thisValue, Object[] args) {
		return RuntimeUtil.toString(env, args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED).toUpperCase() + "!";
	}
});
```

```java
assertEquals("HELLO!", env.evaluateExpression("shout('hello')"));
assertEquals("UNDEFINED!", env.evaluateExpression("shout()"));
assertEquals(1, env.evaluateExpression("shout.length"));
assertEquals("shout", env.evaluateExpression("shout.name"));
```

Sample: `doc_examples/ValuesExamples.java` (`testExposingJavaFunctionsToJavaScript`)

To raise a JavaScript error from Java, throw the result of `RuntimeUtil.typeError(...)`, `rangeError(...)`, `syntaxError(...)` or `error(...)`; any other Java exception surfaces as a plain `Error` (see [Errors](/GaltaJS/UserGuide/Errors)). To reach the current context (executor, output stream, globals) use `JSRuntimeContext.get().getGlobalContext()`.

`setOwnProperty(name, value)` and `setOwnMethod(BaseMethod)` differ in the property descriptor: `setOwnMethod` uses the built-in method descriptor (writable, non-enumerable, configurable) and takes the name from the method's id, which is what `StandardLibrary` does for `parseInt`, `isNaN` and the other global functions.

### The `GlobalFunction` idiom

`StandardLibrary`, `UnitTestLibrary`, `CommonJSLibrary`, `HostLibrary` and `Java` all use the same pattern: one private `BaseMethod` subclass carrying an enum constant, and one `switch` in `call()`.

```java
private static enum FunctionIndex { isNaN, isFinite, encodeURI /* ... */ }

private static final class GlobalFunction extends BaseMethod {
	private final FunctionIndex index;
	GlobalFunction(JSEnvironment env, String name, FunctionIndex index, int length) {
		super(env, name, length);
		this.index = index;
	}
	@Override
	public Object call(Object thisValue, Object[] args) {
		switch(index) {
			case isNaN: /* ... */
			// ...
		}
	}
}
// in configureStandardObjects():
standardObjects.setOwnMethod(new GlobalFunction(env, "isNaN", FunctionIndex.isNaN, 1));
```

It keeps one class per library instead of one anonymous class per function, which matters for libraries with dozens of functions.

## Global values: `StaticLibrary`

When all you need is to expose existing Java values, `StaticLibrary` needs no subclassing:

```java
StaticLibrary lib = new StaticLibrary();
lib.addStaticGlobal("VERSION", "1.2.3");
lib.addStaticGlobal("limits", JSObject.of(JavaScriptEnvironment.create(), "max", 10));
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(lib).build();
assertEquals("1.2.3", env.evaluateExpression("VERSION"));
assertEquals(10, env.evaluateExpression("limits.max"));
```

Sample: `doc_examples/LibrariesExamples.java` (`testStaticLibrary`)

`StaticLibrary.addFunction(BaseMethod)` also exists, but a `BaseMethod` needs an environment in its constructor and the static library is built before any environment exists. Prefer a `GlobalLibrary` subclass for functions.

Java objects added with `addStaticGlobal` are seen through whichever accessor applies to their class: a `JSObject` is a JavaScript object, a `java.util.Map` is a `Map`, a `Date` is a `Date`, and any other class needs either `JavaLibrary` (reflection) or a custom accessor, otherwise property access throws `TypeError: Unknown object type`.

## Gotchas

- `enableGaltaJSExtensions()` registers no library at all: without `StandardLibrary`, `Math`, `JSON` and `console` are undefined.
- A library instance may configure several environments; do not keep per-environment state in fields. Per-environment state belongs to the values you create in `configureStandardObjects` (see `HostLibrary`, which creates its timer table there).
- `configureEnvironment` runs before `build()` finishes, so calling a builder setter from it is fine, but the builder is sealed once `build()` returns.
- `createAccessor` results are cached per concrete class, so returning different accessors for the same class over time has no effect.
- Only one `JavaLibrary` per environment.

## Source

`JSLibrary.java`, `AbstractLibrary.java`, `library/GlobalLibrary.java`, `library/StaticLibrary.java`, `library/CustomLibraries.java`, `rt/builtins/BaseMethod.java`, `rt/builtins/standard/global/StandardObjects.java`, `rt/builtins/standard/StandardLibrary.java`, `JSEnvironment.java` (`Builder.registerLibrary`, `Builder._build`, `buildstandardObjects`).
