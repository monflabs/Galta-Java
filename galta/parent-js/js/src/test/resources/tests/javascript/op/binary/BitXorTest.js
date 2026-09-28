// 12 = 8+4
// 14 = 8+4+2
assertEquals(12,14^2)
assertEquals(12,14^2L)
assertThrows(TypeError, () => 14^2n)
assertEquals(12,14^2.0)
assertEquals(12,14^2.0f)
assertEquals(12,14^2.0)
assertThrows(TypeError, () => 14^2.0m)

assertEquals(12,14^2)
assertEquals(12,14L^2)
assertThrows(TypeError, () => 14n^2)
assertEquals(12,14.0^2)
assertEquals(12,14.0f^2)
assertEquals(12,14.0^2)
assertThrows(TypeError, () => 14.0m^2)
