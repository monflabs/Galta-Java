# Java Interop

With `JavaLibrary` registered (which `GaltaJSEnvironment` does), scripts can load Java classes, construct objects, call methods and static members, read bean properties, pass functions where a functional interface is expected, and reach the Java members of any value. Access can be restricted with an `AccessManager`.

## Enabling it

`GaltaJSEnvironment.create()` registers `JavaLibrary`. On a custom builder, `registerLibrary(new JavaLibrary())` does the same and implies `supportJavaNative(true)`. Without the library, `Java` is undefined (`testJavaLibraryIsOptional`).

## Loading classes and constructing objects

The global `Java` object has a single method, `Java.type(name)`, which loads a class by its fully qualified name through the environment's class loader and returns a constructor-like object. There is no `Packages` object and no implicit package roots. Primitive names (`int`, `long`, `double`, `boolean`, ...) are accepted, mainly for arrays and `instanceof`.

Sample: `doc_examples/JavaInteropExamples.java` (`testLoadClassAndConstruct`)

```js
const Person = Java.type('doc_examples.JavaInteropExamples$Person');
const p = new Person('Ann', 30);
[p.getName(), p.getAge(), p.greet('Bob'), typeof p, p instanceof Person]
// -> [ "Ann", 30, "Hi Bob, I am Ann", "object", true ]
```

`Person` is a plain Java bean declared in the sample class.

## Properties, methods and overloads

Bean properties are exposed as JavaScript properties: `p.name` calls `getName()`, `p.name = 'x'` calls `setName()`. Public fields are accessible as well. Method overloads are resolved from the runtime argument types with these rules (`JavaLibrary.JavaClassMetadata.isAssignable`): an exact class match is preferred; `null` matches any non-primitive parameter; any `Number` can convert to any numeric parameter; `String` and `Character` are interchangeable; a JavaScript function is acceptable for any interface parameter.

Sample: `doc_examples/JavaInteropExamples.java` (`testBeanPropertiesAndOverloads`)

```js
const Person = Java.type('doc_examples.JavaInteropExamples$Person');
const p = new Person('Ann', 30);
p.name = 'Anne';                 // calls setName()
const Math = Java.type('java.lang.Math');
[p.name, p.age, Math.abs(-3), Math.abs(-3.5), Math.max(1, 2)]
// -> [ "Anne", 30, 3, 3.5, 2 ]
```

The library instance has four toggles, all `true` by default: `setUseConstructors`, `setUseFields`, `setUseMethods`, `setUseProperties`.

## Static members

Sample: `doc_examples/JavaInteropExamples.java` (`testStaticMembers`)

```js
const Integer = Java.type('java.lang.Integer');
const Person = Java.type('doc_examples.JavaInteropExamples$Person');
[Integer.MAX_VALUE, Integer.parseInt('12'), Person.of('Zed').name]
// -> [ 2147483647, 12, "Zed" ]
```

Static members are only available on the class object returned by `Java.type()`, instance members only on instances. A `java.lang.Class` value (for example one obtained with `getClass()` or exposed as a global) is a `Class` object with the methods of `java.lang.Class`, not a constructor.

## Functions as functional interfaces

When a Java method takes an interface, a JavaScript function is wrapped in a dynamic proxy (`JavaLibrary.getProxy`). Any single-method interface works: `Runnable`, `Function`, `Comparator`, listeners.

Sample: `doc_examples/JavaInteropExamples.java` (`testFunctionsAsFunctionalInterfaces`)

```js
const p = new Person('Ann', 30);
let ran = false;
p.run(() => { ran = true });                  // JS function -> java.lang.Runnable
[ran, p.map(s => s.toUpperCase() + '!')]      // JS function -> java.util.function.Function
// -> [ true, "ANN!" ]
```

## Collections and arrays

Values of the standard Java collection types get dedicated accessors, whichever way they reach the script: `java.util.List` behaves like an array (indexing, `length`, `push`, `map`, iteration), `java.util.Map` like a `Map` (`get`, `set`, `has`, `size`) and `java.util.Set` like a `Set`. A `List` is not a JavaScript `Array` though (`Array.isArray()` is `false`).

Sample: `doc_examples/JavaInteropExamples.java` (`testJavaCollections`)

```js
const ArrayList = Java.type('java.util.ArrayList');
const list = new ArrayList();
list.push('a', 'b');
const HashMap = Java.type('java.util.HashMap');
const map = new HashMap();
map.set('k', 1);
[list.length, list[1], list.map(s => s.toUpperCase()).join(','), list instanceof ArrayList,
 map.get('k'), map.size, map.has('k'), map instanceof Map]
// -> [ 2, "b", "A,B", true, 1, 1, true, true ]
```

Java arrays are created with the Java syntax `new Type[n]` on a class loaded with `Java.type()`, and behave like arrays.

Sample: `doc_examples/JavaInteropExamples.java` (`testJavaArrays`)

```js
const int = Java.type('int');
const String = Java.type('java.lang.String');
const ints = new int[3];              // Java-style array creation
ints[1] = 5;
const strings = new String[2];
strings[0] = 'x'; strings[1] = 'y';
[ints.length, ints[1], ints[2], String.join('-', strings)]
// -> [ 3, 5, 0, "x-y" ]
```

## The `$` prefix: reaching the Java object behind a value

Every JavaScript value is a Java object. With `supportJavaNative` on, a member name starting with `$` bypasses the JavaScript semantics and accesses the Java member of the underlying object: `(42).$getClass()`, `list.$size()`, `str.$length()`.

Sample: `doc_examples/JavaInteropExamples.java` (`testDollarPrefixReachesTheJavaObject`)

```js
const list = Java.type('java.util.ArrayList');
const l = new list(); l.push(1);
[(42).$getClass().getSimpleName(), 'abc'.$getClass().getName(), l.$size(), l.$isEmpty(), [1,2].$getClass().getSimpleName()]
// -> [ "Integer", "java.lang.String", 1, false, "BuiltinArray" ]
```

## Java objects as globals

`StaticLibrary` exposes Java instances or values as globals for the whole environment (see [Libraries](/GaltaJS/Extending/Libraries)). Per-context globals can be set with `ctx.getGlobalThis().setOwnProperty()`.

Sample: `doc_examples/JavaInteropExamples.java` (`testJavaObjectsAsGlobals`)

```java
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("owner", new Person("Global", 1));
globals.addStaticGlobal("PersonClass", Person.class);
JSEnvironment env = GaltaJSEnvironment.newBuilder().registerLibrary(globals).build();
assertEquals("Hi x, I am Global", env.evaluateExpression("owner.greet('x')"));
// A java.lang.Class global is the Class object, not a constructor: use Java.type() for `new`
assertEquals("doc_examples.JavaInteropExamples$Person", env.evaluateExpression("PersonClass.getName()"));
```

## Restricting access

`new JavaLibrary(AccessManager)` takes an `org.monflabs.util.model.ClassMetadata.AccessManager`. Its methods all return `true` by default; override the ones you need: `canLoadClass(String)`, `canCreateObject(Class)`, `canCreateArray(Class)`, `canProxy(Class)`, `canAccessMember(Class)`, `acceptField(String)`, `acceptMethod(String)`, `acceptProperty(String)`.

Sample: `doc_examples/JavaInteropExamples.java` (`testRestrictingClassAccess`)

```java
JavaLibrary restricted = new JavaLibrary(new ClassMetadata.AccessManager() {
    @Override
    public boolean canLoadClass(String className) {
        return !className.startsWith("java.io.");
    }
});
JSEnvironment env = JSEnvironment.newBuilder()
        .enableGaltaJSExtensions()
        .registerLibrary(new StandardLibrary())
        .registerLibrary(restricted)
        .build();
assertEquals(3, env.evaluateExpression("Java.type('java.lang.Math').abs(-3)"));
try {
    env.evaluateExpression("Java.type('java.io.File')");
    fail();
} catch(JSException e) {
    assertTrue(e.getMessage().contains("cannot be loaded"));
}
```

Note: before this documentation round, `ClassMetadata.findMembers()` inverted the `acceptField` / `acceptMethod` / `acceptProperty` checks, so any custom `AccessManager` hid every member. This is fixed in the `utilities` module.

## Java exceptions

A Java exception thrown by a Java method becomes a JavaScript `Error` whose message starts with `Java Exception:`. The property `__java_exception__` holds the reflection wrapper (`ModelException`); its `getCause()` is the original exception. Uncaught, the error reaches Java as a `JSRuntimeException` (see [Errors](/GaltaJS/UserGuide/Errors)).

Sample: `doc_examples/JavaInteropExamples.java` (`testJavaExceptionsInJavaScript`)

```js
const Integer = Java.type('java.lang.Integer');
let r;
try { Integer.parseInt('abc') }
catch(e) { r = [e instanceof Error, e.message, e.__java_exception__.getCause().getClass().getSimpleName()] }
r
// -> [ true, "Java Exception: NumberFormatException: For input string: \"abc\"", "NumberFormatException" ]
```

## Gotchas

- `Java.type()` needs the fully qualified name; nested classes use `$`.
- A `java.util.List` is array-like but `Array.isArray()` returns `false`; build a `JSArray` when a script needs a real array.
- Number arguments convert between numeric types, so `Math.abs(-3)` picks the `int` overload and `Math.abs(-3.5)` the `double` one; ambiguous calls resolve to the first acceptable overload.
- `Java` itself is not available in `JavaScriptEnvironment`.

## Source

`library/java/JavaLibrary.java`, `library/java/Java.java`, `library/java/JavaClass.java`, `library/StaticLibrary.java`, `rt/builtins/primitives/object/ObjectAccessor.java` (`$` prefix), `galta/parent-utilities/utilities/src/main/java/org/monflabs/util/model/ClassMetadata.java`
