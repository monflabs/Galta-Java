// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

const o1 = {a:1, b:2}
const o2 = {a:1, c:3}

assertFalse( "a" in [][*])
assertTrue ( "a" in [o1][*])
assertTrue ( "a" in [o1,o2][*])
assertTrue ( "b" in [o1][*])

assertTrue ( "a" *in [][*])
assertTrue ( "a" *in [o1][*])
assertTrue ( "a" *in [o1,o2][*])
assertTrue ( "b" *in [o1][*])
assertFalse( "b" *in [o1,o2][*])
