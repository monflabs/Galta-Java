const v = null;

assertEquals(5, null ?? 5)
assertEquals(6, v ?? 6)

assertEquals(1, 1 ?? 2)
assertEquals(1.5, 1.5 ?? 2)
assertEquals("", "" ?? "def")
assertEquals("abc", "abc" ?? "def")
assertEquals(true, true ?? 2)
assertEquals(false, false ?? 2)
