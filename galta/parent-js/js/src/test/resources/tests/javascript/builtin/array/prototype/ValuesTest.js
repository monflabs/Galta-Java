const array1 = ['a', 'b', 'c'];
let s = "";
for (let key of array1.values()) {
	s = s + key +";"
}
assertEquals("a;b;c;",s)
