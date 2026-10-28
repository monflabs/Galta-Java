const Integer = Java.type("java.lang.Integer")
const Double = Java.type("java.lang.Double")
const D = "org.monflabs.util.number.Decimal128"

const i1 = Integer.valueOf("1")
const i2 = Integer.valueOf("2")
const d1 = Double.valueOf("1")
const d2 = Double.valueOf("2")

const v = 1.0
assertTrue(v.$getClass().getName()==D)

assertTrue((i1 + i2).$getClass().getName()=='java.lang.Integer')
assertTrue((d1 + d2).$getClass().getName()==D)
assertTrue((d1 - d2).$getClass().getName()==D)
assertTrue((d1 * d2).$getClass().getName()==D)
assertTrue((d1 / d2).$getClass().getName()==D)
assertTrue((d1 % d2).$getClass().getName()==D)
assertTrue((i1 / i2).$getClass().getName()==D)
assertTrue((i2 / i1).$getClass().getName()=='java.lang.Integer')

// Exact decimal arithmetic
assertTrue(0.1 + 0.2 == 0.3)
assertTrue(String(0.1 + 0.2) == "0.3")

// Division by zero is Infinity, not an exception
assertTrue(d1 / 0.0 === Decimal128("Infinity"))
assertTrue(isNaN(0.0 / 0.0))
