const array1 = ['a', 'b', 'c'];
let s = "";
for (let key of array1[Symbol.iterator]()) {
	s = s + key +";"
}
assertEquals("a;b;c;",s)

assertSame(Array.prototype[Symbol.iterator], Array.prototype.values);
