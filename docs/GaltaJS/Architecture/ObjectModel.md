# Object model

GaltaJS has no wrapper types: a JavaScript value is the Java object that best represents it, and its JavaScript behavior comes from a `JSAccessor` selected by class. Objects are `JSObjectImpl` instances, which are also `JsonObject`s of the Galta JSON library; arrays are `JSArrayImpl`, a `java.util.List`. This page describes the accessor registry, property storage, and how each built-in is represented. The user-facing value table is in [Values](/GaltaJS/UserGuide/Values); writing accessors is in [Accessors](/GaltaJS/Extending/Accessors).

## Accessor registry

`JSEnvironment.getAccessor(Object instance)` looks up `accessorsCache` (a map keyed by the concrete class) and, on a miss, runs `findAccessor(instance)`, an ordered `instanceof` cascade rather than a class-hierarchy walk:

```
AccessorFactory ........ instance.createAccessor(env)      (BuiltinProxy, BuiltinMap, ArrayBuffer, user classes)
UNDEFINED ............... UndefinedAccessor
Number .................. BigInteger -> BigIntAccessor
                          BigDecimal -> BigDecimalAccessor (only when supportBigDecimal), else NumberAccessor
Boolean ................. BooleanAccessor
CharSequence ............ StringAccessor
Symbol .................. SymbolAccessor
JSObject ................ ObjectAccessor
Closure ................. ClosureAccessor             (WithClosure transparency)
java.util.Date .......... DateAccessor
WeakReference ........... WeakRefAccessor
java.util.Map / Set / List  MapAccessor / SetAccessor / JavaListAccessor
libraries ............... CustomLibraries.createAccessor(env, class), JavaLibrary last
otherwise ............... TypeError("Unknown object type ...")
```

`JSAccessor` (abstract, `rt/builtins/JSAccessor.java`) is the meta-object protocol: every operation takes the receiver first (`getPrototype(_this)`, `getOwnProperty(_this, member, defaultValue, receiver)`, `setOwnProperty(...)`, `deleteProperty(_this, member, DESC_CHECK)`, `ownPropertyEntries(...)`, `isExtensible/isSealed/isFrozen`, `getOwnPropertyDescriptor`...), overloaded on `String`, `long` index and `Symbol`. `registerPrototype(Class, proto)`/`getRegisteredPrototype(Class)` is a per-environment singleton registry used by built-ins (`HeadersPrototype`, `ThrowTypeErrorFunction`, `BuiltinWeakMapPrototype`) to memoize their prototype objects; when `isSharedStandardObjects()` is on, registered prototypes are frozen.

## Objects: `JSObjectImpl`

```
JSObjectImpl  (extends JsonObjectAsScriptMap, implements PrivateElementsHolder)
  env, prototype (+ prototypeSet flag)
  string-keyed properties ...... inherited StringPropertyMap / ObjectPropertiesMap<String> -> CustomLinkedMap<K>
  symbols ...................... SymbolPropertyMap (separate storage)
  privateElements .............. PrivateElementsMap (#names)
```

`jsonfactory/CustomLinkedMap` is a custom open-hash map with an intrusive doubly-linked list, not a `LinkedHashMap` and not hidden classes/shapes. Each `EntryImpl<K>` holds `removed`, `hashCode`, `key`, `longKey`, `value`, `descriptor`, and the hash/list links. Three properties matter for the runtime:

- entries are *soft-deleted* (`removed` flag), so spec-conformant iteration survives concurrent mutation;
- `lastIntegerKeyEntry` keeps integer-like keys in an ascending run at the front of the list (spec property order);
- an `EntryImpl` reference stays valid across value writes, descriptor swaps and rehashes, which is what makes the interpreter's `PropIC` sound (see [Interpreter](/GaltaJS/Architecture/Interpreter)).

`ObjectPropertiesMap` carries the integrity level as bit flags `FLAG_SEALED | FLAG_FROZEN | FLAG_PREVENTEXTENSION`; `seal()`/`freeze()`/`preventExtensions()` rewrite descriptors through `updateAllPropertyDescriptors(...)`, with `canAddEntry()/canUpdateEntry()/canRemoveEntry()` hooks. Descriptors are `rt/builtins/PropertyDescriptor` (`of(writable, configurable, enumerable[, getter, setter])`, `isData()`, `isAccessor()`, `isWritable()`, `isConfigurable()`). Symbol writes propagate the flags to the symbol map because it is separate storage.

## Arrays: `JSArrayImpl`

`jsonfactory/JSArrayImpl extends JsonArrayAsArrayList` (so it is a `java.util.List`) with three storage tiers modeled on V8's element kinds:

```
DENSE   no holes            ArrayList[i] = value at JS index (firstItem + i)
HOLEY   interior holes      same layout, HOLE sentinel marks absent slots
SPARSE  util/SparseList     used only when a gap exceeds MAX_HOLEY_GAP (1024)
```

Plus `long firstItem` (leading holes cost nothing), `long lastItem` (explicit length beyond the backing list), an unforgeable `HOLE` object, `boolean lengthWritable`, a lazily created `Map<Long,PropertyDescriptor> indexAccessors` for per-index getters and setters, and a `JSObjectImpl members` for non-index properties. `length` is exposed as a `java.lang.Long`. `JSArray.createSparse(...)`, `isSparse()` and `makeSparse()` expose the sparse tier; `BuiltinArray` is the concrete class created by `JSArray.create(env)`.

## Primitives versus wrapper objects

A primitive and its wrapper share the same Java class (`"a"` and `new String("a")` are both `java.lang.String`). What distinguishes them is an entry in `rt/util/PrimitivePropertyMap`, a `ConcurrentWeakIdentitytMap<Object, JSObjectImpl>` held by the environment (`getPrimitivePropertyMap(autoCreate)`): if the map has a properties object for the instance, it is an object and that `JSObjectImpl` holds its own properties and prototype. `rt/builtins/primitives/ObjectWrapperAccessor.getPropertiesObject(_this, forWrite)` first asks the value itself through `rt/builtins/PropertiesHolder` (`getPropertiesObject()/setPropertiesObject()`, implemented by `BuiltinMap`, `BuiltinSet`, `BuiltinWeakMap`...) and only then falls back to the weak map. The same mechanism lets a `java.util.Date`, which cannot carry properties, behave as a `Date` object.

## Built-in representations

| JavaScript | Java | Notes |
|---|---|---|
| number | `Integer`, `Long`, `Float`, `Double` | Whole literals narrow to `Integer`; overflow promotes per the number flags ([Numbers](/GaltaJS/Extensions/Numbers)). |
| bigint | `java.math.BigInteger` | `BigIntAccessor`. |
| decimal (extension) | `java.math.BigDecimal` | `BigDecimalAccessor`; transcendental functions from vendored `external/ch_obermuhlner_math_big/BigDecimalMath`. |
| string | `String`, `CharSequence` | Concatenation builds ropes: `rt/util/strings/ConsString`, `ConsSequence`, `CharWrapper`, `EmptyCharSequence`. |
| symbol | `rt/builtins/primitives/symbol/Symbol` | Identity objects; the `Symbol.for` registry is process-global across environments; well-known symbols are static fields (`ITERATOR`, `ASYNC_ITERATOR`, `DISPOSE`, `UNSCOPABLES`, `SPECIES`...). |
| object | `JSObjectImpl` (`BuiltinObject`) | See above. |
| function | `BaseCallableObject` hierarchy (`BuiltinFunction`, `BuiltinFunctionTranspiler`, `BaseMethod`, `BaseConstructor`, `Closure`) | `Callable`/`Constructor` interfaces; `Callable.call(_this, Object[])` plus direct-arity overloads up to `MAX_DIRECT_ARITY = 10`. |
| Array | `BuiltinArray` (`JSArrayImpl`) | See above. |
| Date | `java.util.Date` | `DateAccessor`, `DateParser`, `DateUtil` in `standard/date/`. |
| Map / Set | `BuiltinMap implements Map<Object,Object>`, `BuiltinSet implements Set<Object>` | Both over `standard/map/JavaScriptMap extends CustomLinkedMap<Object>` with SameValueZero keys, aware of `supportMixedBigNumber`. |
| WeakMap / WeakSet / WeakRef / FinalizationRegistry | `rt/util/WeakIdentityMap`, `java.lang.ref` | |
| Proxy | `standard/proxy/BuiltinProxy implements AccessorFactory, Callable, Constructor, PrivateElementsHolder` | Dispatched through `rt/builtins/ProxyAccessor`; the `[[Call]]` slot is fixed at creation. |
| ArrayBuffer and views | `standard/typedarrays/`: `BaseArrayBuffer` (a `byte[]` with `maxByteLength`, resizable, transferable), `ArrayBufferView`, `TypedArray`, one subpackage per element kind (`int8`... `float16`, `bigint64`, `uint8clamped`, `dataview`), `sharedarraybuffer`, `standard/atomics/` | `Float16Array` is registered only with `supportFloat16Array`. |
| Error | `rt/builtins/errors/` (`Error`, the native errors, `AggregateError`, `SuppressedError`) | `Error.JAVA_EXCEPTION` (`__java_exception__`) carries a Java cause. |
| Module namespace | `standard/module/ModuleNamespaceObject` | See [Modules runtime](/GaltaJS/Architecture/ModulesRuntime). |

Number-to-string conversion uses the vendored Rhino/V8 code in `external/org_mozilla_javascript/DToA` and `v8dtoa/`.

## JSON integration

`JSObjectImpl` implements `org.monflabs.json.JsonObject` and `JSArrayImpl` the `JsonArray` role, so JSON data needs no conversion in either direction. `JSEnvironment.getJsonFactory()` returns a `GaltaJsJsonFactory extends JavaJsonFactory` whose `createObject()`/`createArray()` produce `BuiltinObject`/`BuiltinArray`, and whose `defaultDecimal()`, `overflowInteger()`, `overflowDecimal()` and `useLongIntegers()` follow the number flags (`supportBigDecimalPromotion` selects `OVERFLOW_DECIMAL.BIGDEC`). `findAccessor` enforces the invariant by throwing `TypeError: JsonObject must be a JSObject as well` if a plain `JsonObject` reaches the engine. The `META-INF/services` registration `JavascriptJsonFactoryService` resolves to the inherited plain `JavaJsonFactory.instance` (the GaltaJS instance field is commented out), so it is not a way to obtain engine-backed JSON.

## `rt/builtins/` layout

- root: shared infrastructure (`JSAccessor`, `AccessorFactory`, `PropertyDescriptor`, `PropertiesHolder`/`AbstractPropertiesHolder`, `Callable`, `Constructor`, `BaseCallableObject`, `BaseConstructor`, `BaseMethod`/`BaseNativeMethod`, `BaseGetter`/`BaseSetter`, `BasePrototype`, `ClassPrototype`, `HomeObject`, `Closure`/`WithClosure`/`ClosureAccessor`, `GlobalThis`, `GlobalVariables`, `NativeObject`, `ProxyAccessor`, `ThrowTypeErrorFunction`);
- `primitives/`: undefined, null, number, string, boolean, symbol, object, array accessors and `ObjectWrapperAccessor`;
- `errors/`: the error hierarchy;
- `privatename/`: `PrivateName`, `PrivateElementsHolder`;
- `standard/`: one subpackage per built-in family (`arguments`, `atomics`, `bigdecimal`, `bigint`, `console`, `date`, `disposablestack`, `finalizationregistry`, `function`, `generator`, `global` (`StandardObjects` is the registry, `StandardLibrary` the installer), `iterator`, `JSON`, `map`, `math`, `module`, `performance`, `promise`, `proxy`, `reflect`, `regexp`, `set`, `typedarrays`, `weakmap`, `weakref`, `weakset`).

## Source

`JSEnvironment.java` (`getAccessor`, `findAccessor`, `registerPrototype`, `getPrimitivePropertyMap`), `rt/builtins/JSAccessor.java`, `rt/builtins/AccessorFactory.java`, `rt/builtins/PropertyDescriptor.java`, `rt/builtins/PropertiesHolder.java`, `rt/builtins/primitives/ObjectWrapperAccessor.java`, `jsonfactory/JSObjectImpl.java`, `jsonfactory/CustomLinkedMap.java`, `jsonfactory/ObjectPropertiesMap.java`, `jsonfactory/JSArrayImpl.java`, `jsonfactory/GaltaJsJsonFactory.java`, `rt/util/PrimitivePropertyMap.java`, `rt/util/strings/`, `rt/builtins/primitives/symbol/Symbol.java`, `rt/builtins/standard/proxy/BuiltinProxy.java`, `rt/builtins/standard/map/JavaScriptMap.java`, `rt/builtins/standard/typedarrays/`, `external/`.
