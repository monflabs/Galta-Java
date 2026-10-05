# Values & Java API

GaltaJS represents JavaScript values with ordinary Java objects. This page gives the mapping, the sentinel for `undefined`, the conversion helpers, the `JSValue` navigation wrapper, how to build objects and arrays from Java, how to call functions across the boundary, and how JSON flows between the two worlds.

## The value model

| JavaScript | Java | Notes |
|---|---|---|
| `number` | `Integer`, `Long`, `Double` (also `Float` with the `f` suffix) | Whole values are narrowed to `Integer`; `int` overflow gives `Double`, or `Long` with `supportLongPromotion`. Array `length` is a `Long`; `Date` getters return `Double`. |
| `bigint` | `java.math.BigInteger` | |
| `decimal` (extension) | `java.math.BigDecimal` | See [Decimal](/GaltaJS/Extensions/Decimal). |
| `string` | `String` (any `CharSequence` is accepted) | |
| `boolean` | `Boolean` | |
| `symbol` | `rt/builtins/primitives/symbol/Symbol` | |
| `null` | `null` | |
| `undefined` | `RuntimeUtil.UNDEFINED` | A singleton sentinel. |
| object | `JSObject` (`BuiltinObject`) | Also a `JsonObject` of the Galta JSON library. |
| array | `JSArray` (`BuiltinArray`) | Also a `java.util.List`; equality is identity, as in JavaScript. |
| function | `rt/builtins/Callable` implementations | |
| `Date` | `java.util.Date` | |
| `Map` / `Set` | `BuiltinMap` (`java.util.Map`) / `BuiltinSet` (`java.util.Set`) | |
| `Promise` | `BuiltinPromise` | See [Async & Event Loop](/GaltaJS/UserGuide/Async). |
| Java objects | themselves | Behaviour comes from an accessor; see [Java Interop](/GaltaJS/UserGuide/JavaInterop) and [Accessors](/GaltaJS/Extending/Accessors). |

## null and undefined

Sample: `doc_examples/ValuesExamples.java` (`testNullAndUndefined`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
// JavaScript null is Java null; undefined is a dedicated sentinel
assertNull(env.evaluateExpression("null"));
assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("undefined"));
assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("({}).missing"));
assertTrue(RuntimeUtil.isNullOrUndefined(env.evaluateExpression("void 0")));
```

`RuntimeUtil.isNull()`, `isUndefined()`, `isNullOrUndefined()` and `isNotNullOrUndefined()` are the corresponding predicates. There is no `NULL` constant.

## Conversions

`RuntimeUtil` implements the abstract operations of the specification. The overloads taking a `JSEnvironment` are the ones to use from Java.

Sample: `doc_examples/ValuesExamples.java` (`testConversions`)

```java
assertEquals("1,2", RuntimeUtil.toString(env, List.of(1, 2)));
assertEquals(12, RuntimeUtil.toNumber(env, "12"));
assertEquals(false, RuntimeUtil.toBoolean(env, ""));
assertEquals(true, RuntimeUtil.toBoolean(env, "0"));
assertEquals("number", RuntimeUtil.typeof(env, 1.5));
assertEquals("string", RuntimeUtil.typeof(env, "x"));
assertEquals("undefined", RuntimeUtil.typeof(env, RuntimeUtil.UNDEFINED));
```

Other useful members: `toInt32(env, v)`, `toPrimitive(env, v)`, `toObject(env, v)`, `call(env, function, this, args)`, `constructObject(env, ctor, args)`, and the error factories described in [Errors](/GaltaJS/UserGuide/Errors).

## Navigating values with JSValue

`jsonfactory/JSValue` is a throw-away wrapper for reading an untyped value from Java: `JSValue.of(env, v)` or `ctx.value(v)` (which reuses the context). It offers type tests (`isObject()`, `isArray()`, `isUndefined()`, ...), typed getters (`intValue()`, `stringValue()`, `booleanValue()`, `bigDecimalValue()`, `arrayValue()`, ...), `get(member)` / `get(path...)` / `getOrDefault(member, dflt)`, `put(member, value)`, and the `call` / `callThis` methods.

Sample: `doc_examples/ValuesExamples.java` (`testNavigatingValuesWithJSValue`)

```java
JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
Object data = env.createScript("({user: {name: 'Ada', langs: ['en', 'fr']}, active: true})", "data.js").executeWithContext(ctx);

JSValue v = ctx.value(data);
assertTrue(v.isObject());
assertEquals("Ada", v.get("user").get("name").stringValue());
assertEquals("fr", v.get("user", "langs", 1).stringValue());   // path form
assertTrue(v.get("active").booleanValue());
assertTrue(v.get("nothing").isUndefined());
assertEquals("dflt", v.getOrDefault("nothing", "dflt").stringValue());
assertEquals(2, v.get("user").get("langs").get("length").intValue());
```

## Creating objects and arrays from Java

`JSObject.create(env)`, `JSObject.of(env, key, value, ...)`, `JSArray.create(env)` and `JSArray.of(env, values...)` build values that scripts see as ordinary objects and arrays. A `java.util.List` given to a script is usable too, but through the Java list accessor (array-like, not a JavaScript `Array`); use `JSArray` when the script expects a real array.

Sample: `doc_examples/ValuesExamples.java` (`testCreatingObjectsAndArraysFromJava`)

```java
JSObject obj = JSObject.of(env, "name", "galta", "tags", JSArray.of(env, "a", "b"));
obj.setProperty("count", 2);
ctx.getGlobalThis().setOwnProperty("obj", obj);

assertEquals("galta:a+b:2", env.createScript("obj.name + ':' + obj.tags.join('+') + ':' + obj.count", "o.js").executeWithContext(ctx));

// The JSValue wrapper offers a fluent alternative
JSValue fluent = ctx.createObject();
fluent.put("x", 1);
fluent.put("list", ctx.createArray().value());
assertEquals(1, fluent.get("x").intValue());
assertTrue(fluent.get("list").isArray());
```

`JSObject` exposes the full property protocol (`getProperty`, `setProperty`, `hasOwnProperty`, `deleteProperty`, `getOwnPropertyDescriptor`, `getPrototype`, `freeze`, ...), overloaded for `String`, index and `Symbol` keys.

## Calling JavaScript functions from Java

`JSValue.call(args...)` calls with `this` set to `null`; `callThis(this, args...)` binds `this`. Both accept up to five direct arguments or a varargs array. At the lower level, any function implements `rt/builtins/Callable` with `call(Object this, Object[] args)`; it needs a current runtime context, so wrap the call in `ctx.with(() -> ...)` when no script is running.

Sample: `doc_examples/ValuesExamples.java` (`testCallingJavaScriptFunctionsFromJava`)

```java
env.createScript("""
    function add(a, b) { return a + b }
    const counter = { n: 10, bump(by = 1) { this.n += by; return this.n } }
    """, "fn.js").executeWithContext(ctx);

// Through JSValue
assertEquals(7, ctx.global("add").call(3, 4).intValue());
JSValue counter = ctx.global("counter");
assertEquals(15, counter.get("bump").callThis(counter.value(), 5).intValue());
assertEquals(16, counter.get("bump").callThis(counter.value()).intValue());

// Through the low-level Callable interface
// (a runtime context must be current on the thread, hence ctx.with())
Callable add = (Callable)ctx.global("add").value();
assertEquals(9, ctx.with(() -> add.call(null, new Object[] {4, 5})));

// Varargs
assertEquals(3, ctx.value(env.evaluateExpression("(...a) => a.length")).call(new Object[] {1, 2, 3}).intValue());
```

## Exposing Java functions to JavaScript

Extend `rt/builtins/BaseMethod` (constructor `(env, name, length)`) and implement `call(Object thisValue, Object[] args)`. The `name` and `length` properties are set for you. Register the method as a global through a library (see [Libraries](/GaltaJS/Extending/Libraries)) or set it on `globalThis`.

Sample: `doc_examples/ValuesExamples.java` (`testExposingJavaFunctionsToJavaScript`)

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder()
        .registerLibrary(new GlobalLibrary() {
            @Override
            public void configureStandardObjects(JSEnvironment env, StandardObjects globals) {
                globals.setOwnProperty("shout", new BaseMethod(env, "shout", 1) {
                    @Override
                    public Object call(Object thisValue, Object[] args) {
                        return RuntimeUtil.toString(env, args.length > 0 ? args[0] : RuntimeUtil.UNDEFINED).toUpperCase() + "!";
                    }
                });
            }
        })
        .build();
assertEquals("HELLO!", env.evaluateExpression("shout('hello')"));
assertEquals("UNDEFINED!", env.evaluateExpression("shout()"));
assertEquals(1, env.evaluateExpression("shout.length"));
assertEquals("shout", env.evaluateExpression("shout.name"));
```

Arguments arrive as raw values (`args` may be shorter than `length`); convert them with `RuntimeUtil`. Throw `RuntimeUtil.typeError(...)` and friends to raise JavaScript errors.

## JSON integration

The engine builds its objects with `GaltaJsJsonFactory`, a `JsonFactory` of the Galta JSON library, so a JavaScript object *is* a `JsonObject` and a JavaScript array *is* a `JsonArray`: no conversion is ever needed. `env.getJsonFactory().parse(text)` parses JSON from Java into script-ready objects, and `JsonObject.stringify(compact)` serializes them.

Sample: `doc_examples/ValuesExamples.java` (`testJsonIntegration`)

```java
// A JavaScript object IS a JsonObject from the Galta JSON library
JsonObject obj = (JsonObject)env.evaluateExpression("({a: 1, b: [true, null]})");
assertEquals("{\"a\":1,\"b\":[true,null]}", obj.stringify(true));
assertEquals(1, obj.get("a"));

// Parsing JSON from Java with the environment's factory produces JavaScript objects
Object parsed = env.getJsonFactory().parse("{\"x\": [1, 2, 3]}");
assertTrue(parsed instanceof JSObject);
assertEquals(List.of(1, 2, 3), list(((Map<?,?>)parsed).get("x")));

// ...and JSON.stringify sees Java-built objects
ctx.getGlobalThis().setOwnProperty("cfg", JSObject.of(env, "port", 8080));
assertEquals("{\"port\":8080}", env.createScript("JSON.stringify(cfg)", "j.js").executeWithContext(ctx));
```

From Java, a JavaScript object or array has all the helpers of a `JsonObject` or a `JsonArray` (`getInt()`, `getString()`, `put()`, `atString(-1)`, `filter()`...), with the same behavior as on a Java container: see [Values](/GaltaJSON/Values). The containers put inside a JavaScript object must be JavaScript ones, created with the environment's factory (`env.getJsonFactory()`, or the object's own `factory()`): a Java `JsonArray` inside a JavaScript object makes `JSON.stringify()` fail. To map Java objects to JavaScript objects and back, use Jackson with the [`json-jackson`](/GaltaJSON/Modules/Jackson) module.

A plain `JsonObject` from another factory must not be handed to a script: the engine requires its objects to be `JSObject`s and throws a `TypeError` otherwise. Parse with the environment's factory or copy into `JSObject.of(...)`.

## How it works

Because values do not share a base class, the engine cannot call methods on them directly. Instead, every Java class is mapped to a `JSAccessor` that implements the object protocol for that class (prototype, properties, descriptors, extensibility). `env.getAccessor(value)` returns it:

```java
JSAccessor acc = env.getAccessor(myObject);
Object prototype = acc.getPrototype(myObject);
Object value = acc.getProperty(myObject, "toString", null);
```

A primitive and its wrapper object use the same Java class (`"abc"` and `new String("abc")` are both `java.lang.String`). The wrapper's properties live in a weak identity map owned by the environment, so a value is an object exactly when that map has an entry for it. The same map lets `java.util.Date` values carry JavaScript properties. Details in [Object Model](/GaltaJS/Architecture/ObjectModel) and [Accessors](/GaltaJS/Extending/Accessors).

## Gotchas

- `JSArray.equals(List)` is `false`: arrays compare by identity. Copy into a plain list (`new ArrayList<>(jsArray)`) to compare contents.
- `length` of an array is a `Long`; `Date` getters such as `getTime()` return `Double`.
- `JSValue` is a view meant for immediate use, not for storage.
- `Callable.call()` needs a current context; `JSValue.call()` provides one.

## Source

`rt/RuntimeUtil.java`, `jsonfactory/JSValue.java`, `jsonfactory/JSObject.java`, `jsonfactory/JSArray.java`, `jsonfactory/GaltaJsJsonFactory.java`, `rt/builtins/Callable.java`, `rt/builtins/BaseMethod.java`, `rt/builtins/JSAccessor.java`
