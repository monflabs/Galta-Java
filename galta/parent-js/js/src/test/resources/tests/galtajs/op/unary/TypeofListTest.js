
const l = [11,22,33]
assertEquals( ["number","number","number"], typeof l.* )
assertEquals( ["undefined","undefined","undefined"], typeof l.*[0] )
assertEquals( ["undefined","undefined","undefined","undefined","undefined","undefined"], typeof l.*[88,89] )
