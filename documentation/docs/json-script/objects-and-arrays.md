---
sidebar_position: 4
---

# Objects and Arrays #

## Dealing with JSON containers (Array and Object)

JsonScript maps the class `Object` to Java JsonObject and `Array` Java JsonArray. They are not meant to be generic maps of lists, but to deal with JSON data. To use more generic container, with a regular behavior, use `Map` (maps to Java `LinkedHashMap`) or `List` (maps Java `ArrayList`).  

## Sequences

JsonScript introduces the notion of a sequence. A sequence is used when a query of an `Array` or an `Object` returns multiple values. Technically, a sequence is an Array that is tagged as a sequence, but only during the query evaluation. Afterwards, when the result is consumed, the sequence is dereferenced to a value following the rules below:  

  - An empty sequence generates the `null` value.  
  - A sequence containing one value generates this value.  
  - A sequence containing multiple values generated an Array with all these values.  

A sequence can also be derefenced with the use of the Array literal syntax `[<sequence>]`. The result is an array that has all the elements of a sequence. It ensures that result is always an array, with 0 to n elements.  

Here is an example 

```javascript
const a0 = []
a0['a'] // Returns null
[a0['a']] // Returns []

const a1 = [{ a: '1' }]
a1['a'] // Returns '1'
[a1['a']] // Returns ['1']

const a2 = [{ a: '1' },	{ a: '2' }]
a2['a'] // Returns ['1','2']
[a2['a']] // Returns ['1','2']
```

Here is how operators work with Array:  

- `.` or `[]`
When accessing a member of an `Array`, the runtime browses the array content and accesses the member of each these entry in a sequence.  
When an object or an array is accessed using `[]` or `.`, it always returns the value in the maps(s) and never the corresponding function. With one exception: when using the operator `.` and when the member is immediately followed by a call `()`:  
  - `myobject.toString` or `myobject['toString']` return the `toString` value, or `null`
  - `myobject.toString()` calls the actual `toString()` method of the container
- `.*` or `[*]`
Return all the values of the left part flattenized.  
- `..`

Now, there is an ambiguity between the array content and the functions when accessed by name. For both `JsonArray` & `JsonObject`, the language distinguishes the functions from the object members when they are called right away:  

```javascript
container.memb    // accesses the member 'memb'of the container
container.func()  // accesses the function 'func' and calls it.  
```
