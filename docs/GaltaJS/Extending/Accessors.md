# Accessors

GaltaJS has no wrapper types: a JavaScript string is a `java.lang.String`, a number is an `Integer` or a `Double`, a date is a `java.util.Date`. Since those classes share no common base, the engine attaches JavaScript behaviour to each Java class through an *accessor*, an instance of the abstract class `rt/builtins/JSAccessor`. Every property read, write, delete, enumeration or prototype lookup on a value goes through the accessor registered for the value's class. Registering an accessor is how a Java class becomes a first-class JavaScript object without reflection.

## The meta-object protocol

`JSAccessor` mirrors the ECMAScript internal methods. Every method takes the receiver as its first argument (`Object _this`), because the accessor itself is shared by all instances of the class. The groups, with their String-keyed signatures (each also exists for `long` indexes and `Symbol` keys, plus a final `Object` dispatcher):

| Group | Methods |
|---|---|
| Identity | `String getClassName(Object _this)` (abstract, the only method you must implement) |
| Prototype | `getPrototype(_this)` (default `null`), `setPrototype(_this, proto)` |
| Integrity | `isExtensible`, `isSealed`, `isFrozen`, `preventExtensions`, `seal`, `freeze` |
| Descriptors | `getOwnPropertyDescriptor(_this, member)`, `getPropertyDescriptor(...)`, `getOwnPropertyDescriptors(...)` |
| Has | `hasProperty(_this, member)`, `hasOwnProperty(_this, member)` (final, built on `getOwnPropertyDescriptor`) |
| Get | `getOwnProperty(_this, member, defaultValue, receiver)` (default: returns `defaultValue`), `getProperty(_this, member, defaultValue[, receiver])` (walks the prototype chain) |
| Set | `setOwnProperty(_this, member, value, PropertyDescriptor, DESC_CHECK, receiver)`, `setProperty(...)` |
| Delete | `deleteProperty(_this, member, DESC_CHECK)` |
| Enumerate | `ownPropertyEntries(_this, strings, symbols, enumerableOnly)`, `ownStringEntries`, `ownSymbolEntries`, `ownEntries` |

`getProperty` is implemented once, on top of `getOwnProperty` and `getPrototype`: it asks the accessor for an own property, then walks `getPrototype(_this)` and asks each prototype (a `JSObject` directly, any other object through its own accessor). So a minimal accessor overrides `getClassName`, `getOwnProperty` for strings and, if inherited members such as `toString` should work, `getPrototype`.

## How an accessor is chosen

`JSEnvironment.getAccessor(Object value)` looks the accessor up in a cache keyed by the value's concrete class and, on a miss, runs `findAccessor(value)`. That method is an ordered `instanceof` cascade, not a class-hierarchy search:

```
value implements AccessorFactory      -> value.createAccessor(env)
UNDEFINED                             -> UndefinedAccessor
Number: BigInteger                    -> BigIntAccessor
        BigDecimal (supportBigDecimal)-> BigDecimalAccessor
        other Number                  -> NumberAccessor
Boolean                               -> BooleanAccessor
CharSequence                          -> StringAccessor
Symbol                                -> SymbolAccessor
JSObject                              -> ObjectAccessor
Closure                               -> ClosureAccessor
java.util.Date                        -> DateAccessor
java.lang.ref.WeakReference           -> WeakRefAccessor
java.util.Map / Set / List            -> MapAccessor / SetAccessor / JavaListAccessor
otherwise                             -> each registered library's createAccessor(env, class), JavaLibrary last
nothing                               -> TypeError: Unknown object type <class>
```

Two consequences: a class that implements both `Map` and `AccessorFactory` is handled by its factory, and a value whose class has no accessor is opaque to scripts (`typeof` works, property access throws).

Sample: `doc_examples/AccessorsExamples.java` (`testWithoutAnAccessorTheValueIsOpaque`)

```java
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("t", new Temperature(1));
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();
try {
	env.evaluateExpression("t.celsius");
	fail();
} catch(JSException e) {
	assertTrue(e.getMessage().contains("Unknown object type"));
}
```

## Route 1: the value describes itself (`AccessorFactory`)

```java
public interface AccessorFactory {
	public JSAccessor createAccessor(JSEnvironment env);
}
```

Implement it on your class when you own the class. The engine calls it once per environment and class, then caches the result.

Sample: `doc_examples/AccessorsExamples.java` (`Point`, `PointAccessor`, `testAccessorFactory`)

```java
public static class Point implements AccessorFactory {
	public final int x, y;
	public Point(int x, int y) { this.x = x; this.y = y; }

	@Override
	public JSAccessor createAccessor(JSEnvironment env) {
		return new PointAccessor(env);
	}
}

/** Read-only x/y properties plus a toString() method; everything else comes from Object.prototype. */
public static class PointAccessor extends JSAccessor {
	private final Object objectPrototype;
	private final BaseMethod toString;

	public PointAccessor(JSEnvironment env) {
		super(env);
		Object objectCtor = env.getStandardObjects().getConstructor("Object");
		this.objectPrototype = env.getAccessor(objectCtor).getProperty(objectCtor, "prototype", null);
		this.toString = new BaseMethod(env, "toString", 0) {
			@Override
			public Object call(Object thisValue, Object[] args) {
				Point p = (Point)thisValue;
				return "Point(" + p.x + "," + p.y + ")";
			}
		};
	}
	@Override
	public String getClassName(Object thisValue) {
		return "Point";
	}
	@Override
	public Object getPrototype(Object thisValue) {
		return objectPrototype;
	}
	@Override
	public Object getOwnProperty(Object thisValue, String member, Object defaultValue, Object receiver) {
		Point p = (Point)thisValue;
		return switch(member) {
			case "x" -> p.x;
			case "y" -> p.y;
			case "toString" -> toString;
			default -> defaultValue;
		};
	}
}
```

```java
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("pt", new Point(3, 4));
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();

assertEquals(12, env.evaluateExpression("pt.x * pt.y"));
assertEquals("object", env.evaluateExpression("typeof pt"));
assertEquals("Point(3,4)", env.evaluateExpression("`${pt}`"));
assertEquals(true, env.evaluateExpression("Object.prototype.hasOwnProperty.call(pt, 'x')"));
assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("pt.z"));
```

Returning `defaultValue` for unknown members is what makes `pt.z` evaluate to `undefined` and lets the prototype chain be searched; returning `null` would mean "the property exists and is null".

## Route 2: a library supplies the accessor for a class

When the class cannot be modified, answer `createAccessor(env, clazz)` from a library. Return `null` for classes you do not handle so the next library, and finally `JavaLibrary`, gets a chance.

Sample: `doc_examples/AccessorsExamples.java` (`testAccessorProvidedByALibrary`)

```java
GlobalLibrary lib = new GlobalLibrary() {
	@Override
	public JSAccessor createAccessor(JSEnvironment env, Class<?> clazz) {
		if(clazz != Temperature.class) {
			return null;   // not ours, let the next library answer
		}
		return new JSAccessor(env) {
			@Override public String getClassName(Object t) { return "Temperature"; }
			@Override public Object getOwnProperty(Object t, String member, Object def, Object receiver) {
				Temperature temp = (Temperature)t;
				return switch(member) {
					case "celsius" -> temp.celsius;
					case "fahrenheit" -> temp.celsius * 9 / 5 + 32;
					default -> def;
				};
			}
		};
	}
};
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("t", new Temperature(100));
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(lib).registerLibrary(globals).build();
assertEquals(212.0, env.evaluateExpression("t.fahrenheit"));
```

Because the cache is keyed by the exact class, a library that wants to cover a hierarchy must test `isAssignableFrom` rather than `==`.

## Prototypes

`getPrototype` defaults to `null`, which means no inherited members: template literals and `String(value)` fail with `TypeError: Cannot convert object ... to a primitive` because there is no `toString` to call. Return `Object.prototype` (as in `PointAccessor` above) or a dedicated prototype object.

Built-in prototypes that must exist once per environment use `JSEnvironment.registerPrototype(Class, proto)` / `getRegisteredPrototype(Class)` as a per-environment singleton registry, keyed by the prototype's own class:

```java
HeadersPrototype p = (HeadersPrototype) env.getRegisteredPrototype(HeadersPrototype.class);
if(p == null) {
	p = new HeadersPrototype(env);
	env.registerPrototype(HeadersPrototype.class, p);
}
```

(`FetchLibrary`'s `HeadersConstructor`, `RequestConstructor` and `ResponseConstructor` follow this idiom.) When `isSharedStandardObjects()` is true the registered prototype is frozen, but no builder option currently sets that flag.

## Built-in mappings for Java types

| Java class | Accessor | Seen by scripts as |
|---|---|---|
| `Integer`, `Long`, `Float`, `Double`, `Short`, `Byte` | `NumberAccessor` | `number` (Long/Float keep their Java class through arithmetic) |
| `java.math.BigInteger` | `BigIntAccessor` | `bigint` |
| `java.math.BigDecimal` | `BigDecimalAccessor` when `supportBigDecimal()`, otherwise the library fallback | `decimal` (see [Decimal](/GaltaJS/Extensions/Decimal)) |
| `Boolean` | `BooleanAccessor` | `boolean` |
| any `CharSequence` | `StringAccessor` | `string` |
| `java.util.Date` | `DateAccessor` | `Date` |
| `java.util.Map` | `MapAccessor` | `Map` (`get`, `set`, `has`, `size`, iteration) |
| `java.util.Set` | `SetAccessor` | `Set` |
| `java.util.List` | `JavaListAccessor` | array-like (`length`, indexes, `push`, `map`, ...; `Array.isArray()` is false) |
| `java.lang.ref.WeakReference` | `WeakRefAccessor` | `WeakRef` |
| `JSObject`, `JSArray` | `ObjectAccessor` | plain objects and arrays |
| anything else | library `createAccessor`, then `JavaLibrary` (reflection) | Java object (see [Java Interop](/GaltaJS/UserGuide/JavaInterop)) |

Sample: `doc_examples/AccessorsExamples.java` (`testBuiltInJavaTypeMappings`)

```java
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("when", new Date(0));
globals.addStaticGlobal("names", List.of("a", "b"));
globals.addStaticGlobal("scores", Map.of("ada", 10));
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();

assertEquals(true, env.evaluateExpression("when instanceof Date"));
assertEquals(1970.0, env.evaluateExpression("when.getUTCFullYear()"));
assertEquals("A,B", env.evaluateExpression("names.map(s => s.toUpperCase()).join(',')"));
assertEquals(10, env.evaluateExpression("scores.get('ada')"));
assertEquals(true, env.evaluateExpression("scores instanceof Map"));
```

## Property descriptors

`rt/builtins/PropertyDescriptor` describes a property: `PropertyDescriptor.of(writable, configurable, enumerable)` for data properties, the overloads with a getter and setter (`BaseCallableObject`) for accessor properties, and `isData()`, `isAccessor()`, `isWritable()`, `isConfigurable()`, `isEnumerable()` to inspect one. Built-in constants such as `DESC_METHOD`, `DESC_PROP_READONLY_CONFIGURABLE` and `DESC_STATICFIELDS` cover the usual cases. Accessors expose descriptors to Java through the accessor API:

```java
Object obj = env.evaluateExpression("Object.freeze({a: 1})");
JSAccessor acc = env.getAccessor(obj);
assertTrue(acc.isFrozen(obj));
PropertyDescriptor desc = acc.getOwnPropertyDescriptor(obj, "a");
assertFalse(desc.isWritable());
assertEquals(1, acc.getProperty(obj, "a", null));
```

Sample: `doc_examples/AccessorsExamples.java` (`testPropertyDescriptorsAreAvailable`)

## Gotchas

- The cache is per concrete class: subclasses of a class you handle are not covered unless your `createAccessor` matches them too.
- `getOwnProperty` must return the `defaultValue` argument for unknown names, not `null` or `UNDEFINED`, otherwise prototype lookup and `in` break.
- No `getPrototype` override means no `toString`, no `hasOwnProperty`, and `${value}` throws.
- Own properties provided by a custom accessor are, by default, invisible to `Object.keys` and `for...in` unless you also override `ownPropertyEntries` / `getOwnPropertyDescriptor`.
- `env.getAccessor(x)` requires a non-null value; `null` is JavaScript `null` and never goes through an accessor.

## Source

`rt/builtins/JSAccessor.java`, `rt/builtins/AccessorFactory.java`, `rt/builtins/PropertyDescriptor.java`, `JSEnvironment.java` (`getAccessor`, `findAccessor`, `registerPrototype`), `rt/builtins/primitives/*`, `rt/builtins/standard/date/DateAccessor.java`, `rt/builtins/standard/map/MapAccessor.java`, `library/java/JavaLibrary.java` (`createAccessor`).
