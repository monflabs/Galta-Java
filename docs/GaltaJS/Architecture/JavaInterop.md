# Java interop

This page describes how a script reaches Java: how a Java object or class gets its JavaScript behavior, how a member name is found, how a call picks an overload and converts its arguments, and what is cached along the way. The user-facing rules are in [Java Interop](/GaltaJS/UserGuide/JavaInterop); the reflection layer used on its own is in [Reflection](/Utilities/Reflection).

## Two layers

The work is split between two modules:

| Layer | Class | Role |
|---|---|---|
| GaltaJS | `library/java/JavaLibrary` | The JavaScript side: the `Java` object, the accessors of Java objects and classes, the script-specific conversions (`undefined`, functions, `Java.type()` classes) and the overload rules that depend on script values. |
| Utilities | `org.monflabs.util.model.ClassMetadata` | Plain reflection: the member caches of each class, overload resolution, argument conversion and invocation. `PojoAccessor` uses it directly, without any script. |

`JavaLibrary` owns a private subclass, `JavaClassMetadata`, which hooks into `ClassMetadata` at a few points:

| Hook | What the subclass changes |
|---|---|
| `createMethodCache()` | Methods are `JSMethodCache`, which also implements `Callable` so that a method is a callable script value. |
| `findField`, `findMethod`, `findProperty`, `findBeanProperty` | Honor the library's `setUseFields`/`setUseMethods`/`setUseProperties` options. |
| `findMembers()` | A method wins over a getter property of the same name. |
| `isAssignable()` | Accepts the script values: a `Java.type()` class, a function. |
| `compareUnrelated()` | Orders two functional interfaces for a function argument. |

## From a value to its Java members

A Java object reaches a script like any other value: `JSEnvironment.getAccessor(instance)` finds the `JSAccessor` of its class (see [Object Model](/GaltaJS/Architecture/ObjectModel)). The cascade handles numbers, strings, `List`, `Map` and `Set` itself. Any other class goes to the libraries, and `JavaLibrary.createAccessor()` comes last:

```
JavaClassImpl (a Java.type() value) ... JavaConstructorAccessor   static members, new, cast
array ................................. JavaArrayAccessor         index, length, Array.prototype
anything else ......................... JavaAccessor(env, class)  instance members
```

The environment caches the accessor by class, so there is one `JavaAccessor` per Java class. It keeps that class's `ClassInfoCache` in a field, which saves a map lookup on every access.

`Java.type(name)` loads the class through the environment's class loader, after `AccessManager.canLoadClass()` agrees. It returns that class's `JavaClassImpl`, a `Constructor`. The same class always gives the same `JavaClassImpl`.

On a JavaScript object, a member name starting with `$` skips the JavaScript properties and goes to the Java members of the object (`ObjectAccessor`). `JavaAccessor` strips the `$` the same way, so `list.$size()` reaches `List.size()` even though the `List` accessor exposes `length` instead.

## Class metadata

`ClassMetadata.getClassInfoCache(class)` returns the `ClassInfoCache` of a class. There are two caches, so that neither the classes nor the `ClassMetadata` are pinned in memory:

- **Classes of the bootstrap, platform and system loaders**, which never unload: a `ConcurrentHashMap` owned by the `ClassMetadata`.
- **Classes of any other loader**, such as a transpiled script or a plugin: a `ClassValue`, so the entry lives and dies with the class.

`JavaLibrary` caches its `JavaClassImpl` objects the same way.

`ClassInfoCache.getMembers(name)` is called on every access, so a hit is a lock-free `ConcurrentHashMap.get()`. On a miss:

1. `MemberNames.isCandidate(name)` checks the name against the class's field and method names, and against the property names implied by its getters. Any other name is answered with an empty result that is *not* cached, so a script probing arbitrary names doesn't grow the cache.
2. `findMembers(class, name)` builds a chain of `MemberCache` objects linked by `nextMember`. Each kind is only looked up when the `AccessManager` accepts it:

```
bean property (java.beans) -> property (getX/isX + setX) -> method(s) -> public field
```

The first member of the chain is the one a script sees. A property hides a field of the same name. In GaltaJS, a method of the same name is moved before the properties: otherwise `executor.shutdown()` could not be called at all, since `isShutdown()` defines a property `shutdown`.

All the public overloads of a method name form a single `MethodCache` list, linked by `nextCallable`. While building it, `findMethod()`:

- skips a synthetic bridge method when the method it bridges is there (`compareTo(Object)` next to `compareTo(T)`);
- for a non-public class (the implementation behind an interface, such as `List.of()`'s classes), keeps only the overloads a public class or interface declares, when they declare any. An extra public overload of the implementation therefore can't change which method a call resolves to. When no public type declares the name, the class's own public methods are kept as a last resort.

## A member read

`JavaAccessor.getOwnProperty(object, name)` looks the name up and reads the first member:

- a field or a property is read and its value returned. A `Class` value goes through `AccessManager.canLoadClass()` first, so reflection can't open a class the manager refuses to load by name;
- a method is returned as the `JSMethodCache` itself: the method value *is* its overload list.

Writing a property goes through `setOwnProperty()`, which converts the value to the field or setter type with `convertObject()` (see below).

## A call

`obj.method(args)` reads the member, then calls the `JSMethodCache`'s `Callable.call(this, args)`:

```
JSMethodCache.call(this, args)
  undefinedToNull(args)                         undefined -> null (copy only if needed)
  findCallable(static?, args)                   cached overload resolution
  convertArguments(convertObject, args)         a new array; varargs collected
  getPublicMethod().invoke(this, converted)     reflection
  checkClassAccess(result)
```

The `static?` flag is `this instanceof JavaClass`. On a `Java.type()` class only static methods apply; on an instance only instance methods apply.

`getPublicMethod()` works around [JDK-4071957](https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4071957): a public method declared by a non-public class can't be invoked through its own `Method`. The same method, declared by a public supertype, is invoked instead, and is cached in the `MethodCache`.

`new C(args)` follows the same path, with the `ConstructorCache` list of `C` and `Constructor.newInstance()`.

The interpreter and the transpiled code use the same accessors and caches: a transpiled script reaches Java at the same cost.

## Overload resolution

`ClassMetadata.findCallable(static?, args, overloads)` takes the runtime classes of the arguments and an overload list, and returns one overload, `null`, or an ambiguity error. Primitive parameters are stored boxed (`int` → `Integer`), with a flag remembering they were primitive. The steps follow Java's own resolution.

### 1. Applicability

`isAssignable(parameterClass, argument)` rates each argument against its parameter as `EXACT`, `POSSIBLE` or `NO`:

| Argument | Parameter | Result |
|---|---|---|
| `null` | primitive | `NO` |
| `null` | any other | `POSSIBLE`, never `EXACT` |
| same class | | `EXACT` |
| instance of a subclass | | `POSSIBLE` |
| `Character` | `String` | `POSSIBLE` |
| one-character `String` | `char` | `POSSIBLE` |
| any `Number` | numeric primitive or wrapper, `BigInteger`, `BigDecimal` | `POSSIBLE` (the conversion may lose precision) |
| `Java.type()` class (GaltaJS) | `Class` | `EXACT` |
| `Java.type()` class (GaltaJS) | a type `Class` is assignable to (`Type`, `Object`...) | `POSSIBLE` |
| function (GaltaJS) | functional interface | `POSSIBLE` |
| anything else | | `NO` |

An overload with the wrong number of parameters, or with one `NO` argument, is not applicable.

### 2. Exact match

An overload where every argument is `EXACT` is chosen at once. `f(int)` and `f(Integer)` both match an `Integer` exactly; the one with the most primitive parameters wins, whatever order reflection lists them in.

### 3. Variable arity

Only when no overload applies with its declared number of parameters, the varargs methods are tried: the trailing arguments are rated against the component type of the last parameter. A fixed-arity overload is always preferred, as in Java.

### 4. Most specific

With several applicable overloads, the maximal ones are kept: those that no other candidate is more specific than. `compareArguments(a, b, args)` says whether overload `a` is more specific than `b`, comparing them position by position:

- **Numeric parameters**, both primitives or wrappers: they are ordered by Java's widening (byte < short < int < long < float < double, char < int). The narrower one is more specific when the argument reaches it by widening: a `Short` prefers `f(int)` to `f(long)`, a `Long` prefers `f(double)` to `f(int)`. When the argument widens to neither of them, as a `Double` for `f(int)`/`f(long)`, the wider one, which loses less, is preferred.
- **A `String` argument** for a `char` parameter and a parameter that takes the string as is (`String`, `CharSequence`, `Object`): the latter is preferred. `f(char)`/`f(Object)` therefore calls `f(Object)` for `"a"`, as for `"ab"`.
- **Related classes**: the subclass is more specific.
- **Unrelated classes**: `compareUnrelated()` may order them; by default they are incomparable. In GaltaJS, for a function argument and two functional interfaces, the interface whose method returns a value is preferred, since a function always returns one: `submit(Callable)` over `submit(Runnable)`.

`a` is more specific than `b` when every position agrees, or is equal. One incomparable position makes the whole signatures incomparable, as in Java. When the positions disagree, the overload that every numeric argument reaches by widening wins. For `Math.max(1, 2.5)`:

| Candidates | Argument `1` (Integer) | Argument `2.5` (Double) | Result |
|---|---|---|---|
| `max(int,int)` vs `max(double,double)` | `int` | `double` | disagree: only `double,double` is reached by widening by both, so it wins |
| `max(long,long)` vs `max(double,double)` | `long` | `double` | disagree: `double,double` wins |
| `max(float,float)` vs `max(double,double)` | `float` | `double` | disagree: `double,double` wins |

`max(double,double)` is the only maximal overload, and it is called with `1.0` and `2.5`.

When exactly one overload is maximal, it is chosen. Two candidates with the same parameter classes (a covariant bridge method, or `f(int)`/`f(Integer)` for a `Double`) count as one, keeping the one with the most primitive parameters. Otherwise, with several maximal overloads or none, the call throws a `ModelException` "Ambiguity between ...", which a script sees as an `Error`.

### The resolution cache

Overload resolution depends only on the *shape* of a call: the static flag and the class of each argument. Two markers complete the shape: `null` arguments, and one-character strings, which are applicable to `char` when longer strings are not. Each overload list caches its resolutions:

- `lastCall`: the last shape and its result, compared without allocating anything. Most call sites always pass the same classes, so this is the usual hit.
- `resolutionCache`: a `ConcurrentHashMap` from shape to result, including "no match". An ambiguity is not cached; it is thrown again on each call.

The overload lists live as long as the class, possibly for the whole JVM for a JDK class. A shape is only put in the map when all its classes come from a loader that can't be unloaded before the method's own class: the bootstrap, platform and system loaders, or the method's loader and its parents. `lastCall` only references the other classes weakly. A script calling `List.add()` with its own classes therefore never pins the script's class loader.

## Argument conversion

`CallableCache.convertArguments(convert, args)` builds the argument array of the reflective call. An argument whose class already is the boxed parameter class is copied as is; this is the usual case, and it skips the converter. The other arguments go through the converter, which for GaltaJS is `JavaLibrary.convertObject(value, parameterClass)`:

1. `undefined` → `null`.
2. A `Java.type()` class → its `java.lang.Class`.
3. A function → a proxy of the parameter interface, unless the parameter is a class or the function already is an instance of it (`Object`): then it is passed as is.
4. Anything else: `ClassMetadata.convertObject()`, which converts a `Number` to the parameter's numeric type (`intValue()`, `longValue()`... then boxing; `BigInteger`/`BigDecimal` by value), a one-character `String` to a `char` and a `Character` to a `String`. Other values are passed unchanged.

The array given by the caller is never modified. For a varargs call, the trailing arguments are converted to the component type and collected into a new array.

## `Java.to` and `Java.from`

`JavaLibrary.toJava(env, value, type)` reads the elements first, whatever the source:
- Java array: `Array.get()`;
- script object or string: its `length` and indexed properties, through its accessor, so holes read as `undefined`;
- any other `Iterable`: iterated.

It then fills a new array of the component type or a new collection, element by element (`toJavaElement()`):
- **primitives, their wrappers and `String`:** the JavaScript conversions (`RuntimeUtil.toNumber`, `toInt32`, `toBoolean`, `toString`), not the lossy Java narrowing used for method arguments, so `'3'` gives `3`;
- **array components:** converted recursively;
- **any other type:** `convertObject()`, as for an argument, followed by a type check.

`AccessManager.canCreateArray()` and `canCreateObject()` apply as for `new`. `fromJava()` copies the elements of a Java array or `Iterable` into `JSArray.of()`, checking `Class` values with `canLoadClass()` like any value returned to a script.

`JavaLibrary.loadClass()` resolves array type names (`int[]`, `java.lang.String[][]`) by stripping the `[]` suffixes and calling `Class.arrayType()`. `new` on such a class creates an array of the given length.

## Functions as Java interfaces

`JavaLibrary.functionalMethod(interface)` finds the single abstract method of an interface, caching the result in a `ClassValue`. The abstract methods that redeclare a public method of `Object` (`Comparator.equals()`) don't count, and several abstract methods of the same name count as one (a generic method redeclared with a narrower signature). Without a single abstract method, the interface doesn't take a function.

`getProxy(function, interface)` checks `AccessManager.canProxy()`, then creates a `java.lang.reflect.Proxy` whose `FunctionInvocationHandler`:

- answers `hashCode`, `equals` and `toString` itself, by identity, so a proxy can be put in a `HashSet` without calling the function;
- runs default methods with `InvocationHandler.invokeDefault()`;
- calls the function for the abstract method, then converts the result to the method's return type: `null` for `void` or `undefined`, and the numeric conversions otherwise (a `ToIntFunction` returning `1.5` gives `1`).

## Cost of an access

Measured on transpiled code, per operation, once warm:

| Operation | Time |
|---|---|
| field read | ~23 ns |
| field write | ~31 ns |
| getter property | ~23 ns |
| setter property | ~33 ns |
| method call, 2 arguments | ~45 ns |
| overloaded method call | ~35 ns |
| constructor | ~23 ns |

What remains is the reflective `invoke()` (about a third), the boxing of primitive results, and two hash lookups (the accessor by class, the member by name). Everything else (member lists, overload resolution, public method lookup, functional method) is computed once per class, name or call shape.

## Source

`library/java/JavaLibrary.java` (`JavaClassMetadata`, `JavaAccessor`, `JavaConstructorAccessor`, `JavaArrayAccessor`, `getProxy`, `convertObject`), `library/java/Java.java`, `library/java/JavaClass.java`, `JSEnvironment.java` (`getAccessor`), `rt/builtins/primitives/object/ObjectAccessor.java` (`$` prefix), `galta/parent-utilities/utilities/src/main/java/org/monflabs/util/model/ClassMetadata.java` (`ClassInfoCache`, `findMembers`, `findMethod`, `findCallable`, `isAssignable`, `compareArguments`, `CallableCache`)
