# GaltaJS  

GaltaJS is an implementation of JavaScript with optional extensions focusing on Java integration, JSON data handling and number precision.


# Implementation Details

To make the integration with Java efficient and seamless, all the types are actually Java types, from primitives to Objects. There is no wrappers like `JSNumber` or `JSBoolean`, but the engine deals directly with the Java types Number (Integer, Long, Double, ...) or Boolean, just to name a few.  

JavaScript Objects can be anything, including existing Java one. For example, the JavaScript `Date` is a simple `java.util.Date` object. To access object properties, because they don't have a common root with the desired methods, the egnine use "accessors". An accessor is a an object that encapsulates the JavaScript access to Java objects. Accessors are provided from a registry and are defined per Java `Class`.  
Example:  

```java
JSEnvironment env = ...; // Get it from somewhere
Object date = new Date();
JSAccessor acc = env.getAccessor(date);
String iso = acc.getProperty(date,"toISOString")
```

How an accessor works depends on the object implementation. We distinguish several cases:

- Objects implemented specifically for the JS Engine.
`JSObject` or `JSArray` are good examples for these. For efficiency reasons, these objects contain all the JavaScript needed data, like a Map of properties, a reference to the prototype and whatever else is needed.

- Java Objects.
`Date` is a good example. Because these objects are defined outside of the JavaScript engine, their JavaScript specific data, like properties or prototype, is stored in a separate object. The environment maintains an optimized WeakMap of the native objects and their javascript data.

Primitives are a special case of Java Object. A java value, like a Java Number/Boolean/String, is considered a JavaScript primitive is their is no associated object in the above Map. If there is one, then the value is a JavaScript object.  

```javascript
const s1 = "a string" // Javascript primitive represented by a Java String
const s2 = new String("a string)" // Javascript object represented by a Java String and an entry in the map
```
This avoids having JavaScript specific wrappers in Java, like `JSNumber`, `JSString` or `JSBoolean`, and facilitates the data exchange between JavaScript and Java.


## Known JavaScript Incompatibilities
  
### Function Argument

When no running in strict mode, the actual function arguments and the `arguments` pseudo variables are disconnected. A as result, modifying a value in one is not reflected in the other (they are 2 copies of the same data).
See: `ecma_3/ExecutionContexts/10.1.3-1.js`


### Date

The engine uses native `java.util.Date`, which makes the communication between Java and JavaScript easy. If both the Java Date and the Javascript specification are based on the number of milliseconds since 1/1/1970, they have actually a different encoding. The difference are not for dates around our era, but for far away dates. In practice, this has no impact unless you you the integer encoding to serialize/deserialize a data between system.  


### Variables

`var` cannot be deleted even when the variables is created in an `eval()` statement.  
```js
eval("x=1")
delete x; // <- returns false
```
See: `ecma_3/ExecutionContexts/10.1.4-1.js`

`var x` also does not override a variable from `with` or `catch`. The later might be a little bit more problematic, and should not be too hard to fix.
See: `  ecma_3/ExecutionContexts/regress-448595-01.js`

RegExp $1...$9 properties are deprecated and not supported
See:ecma_3/extensions/regress-220367-002.js


### eval()

`eval()` implementation is limited and works like strict mode. It also does not handle Direct vs Indirect, as defined by the spec.  
  

## JavaScript Extensions


### Dealing With Numbers

Numbers are native Java numbers, inheriting from `Number`. Internally, the script engine uses `Integer`, `Long`, `Double` and even `BigInteger` or `BigDecimal`. It also understands `Byte`, `Short` and `Float` as well, although the result of mathematical operation don't return these data types.  

Beyond the support of `BigInteger` that map to a JavaScript `BigInt`, the engine also support a `BigDecimal` as an extension. By default, `BigDecimal` are decimal128, as defined in `JSEnvironment`.

To make all the types easily available as literal, the engine provides the following suffixes (case insensitive):  

- `const i = 0    // Integer`
- `const i = 0L   // Long`
- `const i = 0.0  // Double`
- `const i = 0.0f // Float`
- `const i = 0n   // BigInteger`
- `const i = 0m   // BigDecimal`



### Operations on numbers

By default, following the JavaScript specification, the engine operates with `Integer` when then can fit the value (ex: 0, 1, -736357) and then `Double` when they don't fit (ex: 1e10). When the result of an operation on integers don't fit an integer, then the result is a double.  

```js
const a = 1492553851;
const b = 1144678303;
a*b // result=Double(1708494009298794800)  this is mathematically invalid
```

GaltaJS has an extension to use `Long` when a result does not fit an integer.  

```js
const a = 1492553851;
const b = 1144678303;
a*b // result=Long(1708494009298794853)  this is mathematically valid
```

Even if the int promotion extension is not enabled, a `long` result is returned if one on the operand is a long:

```js
const a = 1492553851L;
const b = 1144678303;
a*b // result=Long(1708494009298794853)  this is mathematically valid
```

Similarly, the division of two integers may lead to a double result if the result is not an integer (ex: 4/3 results to Double(1.3333333333), while 4/2 results to Integer(2))

0 is an integer, while -0 is a double to carry over the negative. +0 is a double as well. Both convert to integer 0.


GaltaJS has extensions to better use big numbers.

1- There is an extension to mix them with the other numbers. This allows expression like `1n + 2`.  

2- There is another extension to  promote every integer arithmetic to `BigInt`. For example, a `Long` operation can generate a BigInt:

```js
const a = 4745484599433L;
const b = 4745484599433L;
a*b // result=BigInt(22519624083455780463921489n)  instead of Double(2.251962408345578e+25)
```

3- Then, the use of `BigDecimal` for floating point can be used instead of `Double`, thus making all the JS calculations more precise at the expense of performance.  


### Extended Operators

### Extended operators ###

Some of these operators, like null coalescing, are now part of the ECMA specification. Here are the ones supported by GaltaJS:  

- `?.`: access a member and return null if it does not exist
  Ex: 
- `?[`:  access a member and return null if it does not exist
  Ex: 
- `!.`: access a member and generate an exception if it does not exist.
  Ex: 
- `![`:  access a member and generate an exception if it does not exist.
  Ex: 
- `?:`: return the tested value if not null, or the other if null.
  Ex: 


The engine also supports the pipe operator:  
//todo


### Java Integration

The engine features a Java integration library that provides several capability.


#### Access Java classes

Java classes are available via `Java.type`. Once loaded, a class can be instanciated using the `new` operator.

```js
const MyClass = Java.type("my.package.MyClass")
var o = new MyClass()
```


#### Access To Native Methods

This is an engine option to activate.  
Because each JavaScript value is an actual Java Object, accessing a method `m()` calls the JavaScript method of the object. Using the prefix `$`, like `$m()` calls the method of the Java class.

```js
const d = new Date()
d.toString()    // 'Thu May 07 2026 21:25:27 GMT-04:00', javaScript way
d.$toString()   // 'Thu May 07 21:25:27 EDT 2026', java way
```


#### new

The `new` operator extends the JavaScript syntax to support Java Array.

`new <expr>[][<size>]`  


`<expr>` is a a simple expr of the form `name[.member ...]`. A typical use is the following:  

Note that `new a.b()` doesn't reference the java class `a.b`, but the member `b` of the identifier `a`.  



#### Expressions as statements

The language support expressions, like `1` or `{a: 98}` as statement in main program and return that value.  

For example, for following program:  

```js
const v=88;
{ g: v }
```

will return an object with one property, `g` of value `88`.  

In rare cases, this can lead to an ambiguity with the labels, ex:  

```js
{ loop: a(); }
```

This won't compile as part of the main program as `{ loop:` is parsed as the beginning of an object literal, but then fail.  

As this capability only applies to main statements, the following ones are valid:  

```js
for(let i=0; i<3; i++) { loop: for(...) }
```


#### Dealing with JSON data

GaltaJS maps the class `Object` to Java `JsonObject` and `Array` Java `JsonArray` and can be manipulated as such. Note that JavaScript Object or Array are a little bit more heavyweight than standard JsonObject/JsonArray because they carry extra information and behaviors (for example `Array` supports sparse arrays). 

To better deal with JSON data and query it using JSON path, GaltaJS introduces the notion of a sequence. A sequence is used when querying an `Array` or an `Object` returns multiple values.


Here is how operators work with Array:  

- `.` or `[]`
When accessing a member of an `Array`, the runtime browses the array content and accesses the member of each these entry in a sequence.  
When an object or an array is accessed using `[]` or `.`, it always returns the value in the maps(s) and never the corresponding function. With one exception: when using the operator `.` and when the member is immediately followed by a call `()`:  
  - `myobject.toString` or `myobject['toString']` return the `toString` value, or `null`
  - `myobject.toString()` calls the actual `toString()` method of the container
- `.*` or `[*]`
Return all the values of the left part flattenized.  
- `..`
Search for member through all the tree and return a sequence.  
- Sequence handling.  
When a sequence is deferenced, when used in a expression, it is first dereferenced using the following pattern:
  - An empty sequence generates the `null` value.  
  - A sequence containing one value generates this value
  - A sequence containing multiple values generated an Array with all these values


```javascript
const a0 = []
const r0 = a0['a'] // Returns null

const a1 = [{ a: '1' }]
const r1 = a1['a'] // Returns '1'

const a2 = [{ a: '1' },	{ a: '2' }]
const r2 = a2['a'] // Returns ['1','2']
```


Now, there is an ambiguity between the array content and the functions when accessed by name. For both `JsonArray` & `JsonObject`, the language distinguishes the functions from the object members when they are called right away:  

```javascript
container.memb    // accesses the member 'memb'of the container
container.func()  // accesses the function 'func' and calls it.  
```


#### Exceptions are Java exceptions

Exceptions emitted while running JavaScript code are wrapped into a `JSRuntimeException`. This contains the reference a native exception, if any. It also contains information like the JavaScript position in the code.



#### with() statement  

`with` is supported in non strict mode. Now there is a slight difference with the standard as a function returned in a with
statement is a closure that wraps the function. This allows the call operator to execute the method with a `this`. That makes the
transpiler a lot simpler but has a side effect: the object returned is a wrapper, thus a different object than the original
see below:

```
const f1 = myObject.f
with(myObject) {
  const f2 = f;
  assertNotEquala(f1,f2);
}
```

Symbol.Unscopables but is not implemented in with(), with() should anyway be avoided. 


In practice, `with` is deprecated and this behavior should not have impact on real scripts, besides JS compatibility tests.
