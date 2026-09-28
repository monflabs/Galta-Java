const s1 = Symbol()
const s2 = Symbol()
assertEquals(s1, s1)
assertNotEquals(s1, s2)

const s3 = Symbol("a")
const s4 = Symbol("a")
assertNotEquals(s3, s4)

assertThrows( () => new Symbol() );
assertThrows( () => new Symbol("a") );

const o1 = Symbol("obj")
assertThrows( () => o1.one = 1 );
assertEquals(undefined, o1.one)


assertEquals("a", s3.description)
assertEquals("a", s4.description)
assertEquals("obj", o1.description)


const symbOne = Symbol(1)
const symbOnep = Symbol(1)
const symbTwo = Symbol("2")
const symbThree = Symbol("3")
const so = {}
so[symbOne] = 11;
so[symbOnep] = 111;
so[symbTwo] = "22";

assertEquals(11, so[symbOne])
assertEquals(111, so[symbOnep])
assertEquals("22", so[symbTwo])
assertEquals(undefined, so[symbThree])
assertEquals("1122", so[symbOne]+so[symbTwo])


const s_number = new Number(12);
s_number[symbOne] = 91;
assertEquals(91, s_number[symbOne])

const s_string = new String("ABC");
s_string[symbOne] = 92;
assertEquals(92, s_string[symbOne])

const s_bool = new Boolean(true);
s_bool[symbOne] = 93;
assertEquals(93, s_bool[symbOne])


// Standard symbols
assertEquals("Symbol.asyncIterator", Symbol.asyncIterator.description )
assertEquals("Symbol.hasInstance", Symbol.hasInstance.description )
assertEquals("Symbol.isConcatSpreadable", Symbol.isConcatSpreadable.description )
assertEquals("Symbol.iterator", Symbol.iterator.description )
assertEquals("Symbol.match", Symbol.match.description )
assertEquals("Symbol.matchAll", Symbol.matchAll.description )
assertEquals("Symbol.replace", Symbol.replace.description )
assertEquals("Symbol.search", Symbol.search.description )
assertEquals("Symbol.species", Symbol.species.description )
assertEquals("Symbol.split", Symbol.split.description )
assertEquals("Symbol.toPrimitive", Symbol.toPrimitive.description )
assertEquals("Symbol.toStringTag", Symbol.toStringTag.description )
assertEquals("Symbol.unscopables", Symbol.unscopables.description )

// Symbol.species is a getter-only accessor returning `this`, not a static
// value, so a subclass's own [Symbol.species] resolves to the subclass.
assertEquals(Array, Array[Symbol.species]);
assertEquals(Map, Map[Symbol.species]);
assertEquals(Set, Set[Symbol.species]);
assertEquals(Promise, Promise[Symbol.species]);
assertEquals(RegExp, RegExp[Symbol.species]);

class MyArray extends Array {}
assertEquals(MyArray, MyArray[Symbol.species]);
