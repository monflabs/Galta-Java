const s = new Set();
assertEquals(0, s.size)

s.clear()
assertEquals(0, s.size)

s.add(1);
s.add(2);
assertEquals(2, s.size)

s.clear()
assertEquals(0, s.size)
