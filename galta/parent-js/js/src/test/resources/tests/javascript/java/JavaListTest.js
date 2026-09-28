const List = Java.type("java.util.ArrayList");

const Collectors = Java.type("java.util.stream.Collectors")

const list = new List();

assertEquals(0,list.length)
assertFalse(list.indexOf("a")>=0)

assertTrue(list.$isEmpty())
assertEquals(0,list.$size())
assertFalse(list.$contains("a"))


list.push("a");

assertEquals(1,list.length)
assertTrue(list.indexOf("a")>=0)

assertFalse(list.$isEmpty())
assertEquals(1,list.$size())
assertTrue(list.$contains("a"))

let loop; let e;

loop = 0
for(e of list) {
	assertEquals("a",e)
	loop++
}
assertEquals(1,loop)


list.push("b")
list.$add("c")

let s = ""
list.forEach( v => {s = s+v} )
assertEquals("abc",s)

const l1 = list.$stream().filter( v => v=='a' || v=='c' ).collect(Collectors.toList());
assertEquals("a,c",l1.toString())
assertEquals("[a, c]",l1.$toString())

