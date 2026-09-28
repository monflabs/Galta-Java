const m = new Map();
assertEquals(0, m.size)

m.clear()
assertEquals(0, m.size)

m.set(1,"1");
m.set(2,"2");
assertEquals(2, m.size)

m.clear()
assertEquals(0, m.size)
