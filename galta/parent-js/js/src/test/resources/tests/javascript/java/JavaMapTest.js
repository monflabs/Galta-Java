const Map = Java.type("java.util.HashMap");

const map = new Map();

assertTrue(map.$isEmpty())
assertEquals(0,map.$size())
assertEquals(0,map.size)

assertFalse(map.$containsKey("a"))
assertFalse(map.has("a"))
assertEquals(null,map.get("a"))

map.set("a", "1")

assertFalse(map.$isEmpty())
assertEquals(1,map.$size())
assertEquals(1,map.size)

assertTrue(map.$containsKey("a"))
assertTrue(map.has("a"))
assertEquals("1",map.get("a"))

let loop; let k;

loop = 0
for(k of map.keys()) {
	assertEquals("a",k)
	loop++
}
assertEquals(1,loop)

loop = 0
for(let v of map.values()) {
	assertEquals("1",v)
	loop++
}
assertEquals(1,loop)

loop = 0
for(const e of map) {
	assertEquals("a",e[0])
	assertEquals("1",e[1])
	loop++
}
assertEquals(1,loop)
