---
sidebar_position: 20
---

# Differences with JavaScript #

## Compiler

## Errors

The compiler catches (potential) errors as early as possible, without waiting for the runtime to check them:  
  - Variables cannot be redefined in the same block
  - Cannot assign a value to a const/function varoables, or an undeclared variable


## Types

### Primitives vs Objects

JsonScript does not make a difference between primitive types and Objects, like in JavaScript. Therefore, everything is an object but certain objects, namely String, Number or Boolean also behave like primitives. This introduces some minor changes compared to JavaScript:  
  - `"a string"` and `new String("a string")` are identical.  
    Actually, `new String(s)` returns `s`.  

Numbers are real java numbers. Internally, the script engine uses `Integer`, `Long`, `double` and even `BigDecimal`. This can affect the result of computations.


### Global Java identifiers

The java library makes the following globals, representing java classes or primitive types:  

```javascript
  char
  byte
  short
  int
  long
  float
  double
  boolean
  String
  BigInteger
  BigDecimal
  Object
  Array
  Java
```  

### undefined and null, and NULL

`undefined` does not exist in JsonScript, mainly because it does not exist in Java. It only handles the `null` value.  
Now, to efficiently deal with JSON access, we introduce a new null value called `NULL`. Semantically, it works like `null` but it propagates when accessing members, instead of raising a NullPointerException.  

```javascript
const n1 = null;
n1.a; // -> throws exception

const n2 = NULL;
n2.a; // -> resolves to NULL (propagated)
```

Currently, the NULL value is only returned by Array and Object when a member does not exist.  

```javascript
const o = {}
o.a; // -> NULL
o.a.b; // ->NULL
```

### Type coercion

JsonScript enforces that the right types are used and doesn't do coercion, like automatically transforming values to `String` or `Number`. It throws errors when the right type is not used. For example, mysstring.charAt("2") fails as the string `"2"` is not automatically converted to the number `2`, and thus generates an error.  
It also validates the arguments with the method signature so `mysstring.charAt()` and `mysstring.charAt(1,2)` will both fail because the signature of the method is `charAt(pos: int)`.  

### Use of prototypes

Prototypes methods are meant to be used for the types they are designed for, and they do not try to convert the argument to that type.  
String.prototype.charAt.call( 123456, 1 ) will fail instead of returning 2.  


### Numbers

Numbers are real java numbers, inheriting from `Number`. Internally, the script engine uses `Integer`, `Long`, `Double` and even `BigInteger` or `BigDecimal`. This can affect the result of computations.


### new

The `new` operator works a bit differently from JavaScript to better target Java classes. The syntaxe is:  
`new <expr>(<args>)`  
or for arrays:  
`new <expr>[][<size>]`  

`<expr>` is a a simple expr of the form `name[.member ...]`. A typical use is the following:  

```javascript
const MyClass = Java.type("my.package.MyClass")
var o = new MyClass()
```  

Note that `new a.b()` doesn't reference the java class `a.b`, but the member `b` of the identifier `a`.  

### String handling

Supports a subset of ES6 template strings:  `${expressions}` within a back-ticked string. It does not support tagged strings.  

Ex: \``My name is ${NAME}`\`

## Built-in functions

### Global Functions

The runtime proposes a few runtime functions that mimics their JavaScript counterparts, although their behavior might have some slight differences:  

  - `parseInt(string,radix)`
  - `parseFloat(string)`


### Objects and Arrays

#### Object.create

The parameters `proto` & `propertiesObject` are ignored as prototypes are not supported for custom objects. Adding them to JsonObject/JsonArray is not a big deal, but it will fail when these are used as wrapper atop other JSON libraries, like GSON.  

#### Object literals

When a calulated property name is `null`, then the property is not added to the object, thus allowing properties to be added conditionally. On the other hand, if `null` is used sstatically, then the `null` key. is used.  

```javascript
const n1 = "N1", n2=null, n3="N3"
assertEquals( {N1: 1, N3: 3}, {[n1]: 1, [n2]: 2, [n3]:3} )
assertEquals( {N1: 1, null: 2, N3: 3}, {[n1]: 1, null: 2, [n3]:3} )
```


#### Array.length

`length`: is a property of an Array and not of its prototype
`@@iterator`: an array iterator is actually a Java iterator that works with `for(v of x)`. Because `JsonArray` implement iterable, there is no JavaScript like iterator.


#### Array.sort()

The `sort()` method doesn't convert the items to strings before they are sorted. It sort the values with their types, so a number array is sorted properly.  


## Operators


### Iterators ###

`Iterators` do not follow the JavaScript iteration protocols, but the Java one (e.g. `Iterator` are Java iterators).  An iterator thus has the methods `hasNext()`, `next()` and `remove()` instead of the simple `next()` method returning `{value,done}`. Moreover, the `next(value)` method is not available.


### Object accessor ###
The dot notation `.member` or the array one `['member']` can be used to access object members. By default, this operator throws an exception when the member does not exist. If this behavior is ok for programs, it becomes cumbersome when evaluating expressions. Scriptor features an option where accessing a missing member returns `null`. This option is available as a `DSEnvironment` option.
Note that returning null or throwing an exception can be controlled through the extended operators `?.` or `!.` (see bellow).


### Extended operators ###
- `?.`: access a member and return null if it does not exist
- `?[`:  access a member and return null if it does not exist
- `!.`: access a member and generate an exception if it does not exist.
- `![`:  access a member and generate an exception if it does not exist.
- `?:`: return the tested value if not null, or the other if null.

## Exceptions are Java exceptions

JsonScript deals with native exceptions


## Interpreter vs Compiler

### delete ###  

A `var`` variable cannot be 'deleted' (removed from scope) using the delete operator in compiled mode. As the variable are static when compiled, its value
is simply set to null. Anyway, using `var` should be avoided.  

Also, deleting an array item doesn't create a sparse array (does not exist in JsonScript) but removes the element from tha array if possible (not possible with java arrays)

