const b = "1234"

assertEquals("1234",b.padEnd(2))
assertEquals("1234",b.padEnd(4))
assertEquals("1234nu",b.padEnd(6,null))
assertEquals("1234",b.padEnd(6,''))
assertEquals("1234  ",b.padEnd(6))

assertEquals("1234..",b.padEnd(6,'.'))
assertEquals("1234XYX",b.padEnd(7,'XY'))
assertEquals("1234XYXY",b.padEnd(8,'XY'))
