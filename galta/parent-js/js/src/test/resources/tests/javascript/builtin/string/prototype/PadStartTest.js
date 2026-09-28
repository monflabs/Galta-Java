const b = "1234"

assertEquals("1234",b.padStart(2))
assertEquals("1234",b.padStart(4))
assertEquals("nu1234",b.padStart(6,null))
assertEquals("1234",b.padStart(6,''))
assertEquals("  1234",b.padStart(6))

assertEquals("..1234",b.padStart(6,'.'))
assertEquals("XYX1234",b.padStart(7,'XY'))
assertEquals("XYXY1234",b.padStart(8,'XY'))
