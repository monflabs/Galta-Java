// LIST tests
// The extensive combinations are implemented with the '+' operators
// We just do a simple assement that it works with this operator

const int = Java.type("int")
const long = Java.type("long")

assertTrue ( [1][*] instanceof int)
assertTrue ( [1,2][*] instanceof int)
assertTrue ( [1,2,"abc"][*] instanceof int)

assertTrue ( [1][*] instanceof [int][*])
assertTrue ( [1,2][*] instanceof [int][*])
assertTrue ( [1,2,"abc"][*] instanceof [int][*])

assertTrue ( [1][*] instanceof [int,long][*])
assertTrue ( [1,2L][*] instanceof [int,long][*])
assertTrue ( [1,2,"abc"][*] instanceof [int,String][*])


assertTrue ( [1][*] *instanceof int)
assertTrue ( [1,2][*] *instanceof int)
assertFalse( [1,2,"abc"][*] *instanceof int)

assertTrue ( [1][*] *instanceof [int][*])
assertTrue ( [1,2][*] *instanceof [int][*])
assertFalse( [1,2,"abc"][*] *instanceof [int][*])

assertFalse( [1][*] *instanceof [int,long][*])
assertTrue ( [1,2L][*] *instanceof [int,long][*])
assertFalse( [1,2,"abc"][*] *instanceof [int,String][*])
