---
sidebar_position: 3
---

# JsonScript Syntax  

The JsonScript syntax is actually Javascript, with all the familiar constructs except classes. The grammar is actually the ECMAScript one with a few changes. Because of this, the documentation bellow is succint, emphasizing on the differences with JavaScript when they exist.   

## Statements  
Statements are separated using a semi colon `;`, which is optional if the statements are on different lines:
```
let i=0; let j=5;
let y=23
let z=45
```  
Statements can be grouped into a block delimted by `{}`, which also defines a scope for the variables.  

## Comments
Comments can be single line, or multi line:  
```js
// My single line comment
/*
 * My multiline
 * comment
*/
```  

## Declaring variables
Following modern JavaScript recommendations, the variables in JsonScript must be explicitly declared, using one of the following keywords:  
`const let var`
Example:
```js
const a=2, b=3;
let i=0;
```
Note: the `var` construct is supported to maximize the JavaScrip compatibility, but should be avoided.  

The declaration statements support variable destructuration:  
```js
const array = { A:1, C: 3}
const { A, B, C } = array
```

## Literals  
JsonScript deals with with the following primitives: `Numbers`, `Boolean` and `Strings`. They map to their JVM Object counterparts, namely `java.lang.Number`, `java.lang.Boolean` & `java.lang.String`.  

### Null values  
Null values are simply `null`, like in Java or JavaScript. Note that `undefined` does not exist, as it has no counter part in the JVM.  

### Numbers  
Numbers are any JVM Numbers, inheriting from `java.lang.Number`. It includes integers, decimal numbers and even big numbers (`BigInteger` and `BigDecimal`). To handle these nuances in literals, JsonScript checks the actual number and find the best match:  
```js
const v = 79;  // Integer
const v = 123456789123456; // Long
const v = 123456789123456789123456789123456789123456789; // BigInteger
const v = 1.2; // Double
const v = 1.123456789123456789; // BigDecimal (by default, when length>16)
```  
Note: runtime options can force all the integers to be `BigInteger` and all the decimals to be `BigDecimal`, for maximum precision at the expense of performance.  

Also, JsonScript uses suffixes to force the type of a number:  
```js
const v = 1234i; const v=1234I // Integer
const v = 1234l; const v=1234L // Long
const v = 1234n; const v=1234N // BigInteger
const v = 12.4f; const v=12.4F // Float
const v = 12.4d; const v=12.4D // Double
const v = 12.4m; const v=12.4M // BigDecimal
```  
Finally, the syntax supports infinity numbers `+Infinity` `-Infinity`, not a number `NaN`, hexedecimal numbers `Ox1ac5`, octal `01234`, binary `0b11010`, as well `_` as a thousand separator. The legacy JavaScript octal notation with just a leading `0` is not supported.  

## Declaring functions
Functions are declared using the `function` keyword:  
```js
function f() {
  return "xyz"
}
```  
The lambda syntax is also available:  
```js
const f = () => 45
const g = () => { return 45; }
```

## Control flow  
The standard JavaScript control flows are available, with or without bloacl `{}`:  
```js
if(cond) {
  ...
} else {
  ...
}
do {
  ...
} while(cond);
while(cond) {
  ...
}
for(let i=0; i<10; i++) {

}
```

## Creating Object and Arrays  
The `Object` and `Array` class are similar to JavaScript. Note that internally they are instances of `org.monflabs.json.JsonObject` and `org.monflabs.json.JsonArray`, which are respectively Java `Map` and `List`.  

There are two main differences with JavaScript:  
  - `Object` and `Array` don't have a prototype assigned to their instances. This is because these instances are Java collections that can't hold this information. The prototypes actually exist, but they are the shared between all the instances.

Example:  
```js
const o = new Object()
const oi = { a: '1', b: '2'}
const a = new Array()
const ai = [1,2,3]
```

Object literals also support calculated properties, although the language does not support `Symbol`:  
```js
let prop = 'foo';
let o = {
  [prop]: 'hey',
}
```

