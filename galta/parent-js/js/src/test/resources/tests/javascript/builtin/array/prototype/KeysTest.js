const array1 = ['a', 'b', 'c'];

let s = "";
for (let key of array1.keys()) {
	s = s + key +";"
}

assertEquals("0;1;2;",s)
