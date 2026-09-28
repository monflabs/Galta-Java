const Integer = Java.type("java.lang.Integer")
const Double = Java.type("java.lang.Double")

const i1 = Integer.valueOf("1")
const i2 = Integer.valueOf("2")
assertTrue(i1.$getClass().getName()=='java.lang.Integer')
assertTrue(i2.$getClass().getName()=='java.lang.Integer')

const d1 = Double.valueOf("1")
const d2 = Double.valueOf("2")
assertTrue(d1.$getClass().getName()=='java.lang.Double')
assertTrue(d2.$getClass().getName()=='java.lang.Double')


// Decimal literal is a Decimal
const v = 1.0
console.log(v.$getClass())
assertTrue(v.$getClass().getName()=='java.math.BigDecimal')


// Operations are Decimal
const add1 = i1 + i2
assertTrue(add1.$getClass().getName()=='java.lang.Integer')
const add2 = d1 + d2
assertTrue(add2.$getClass().getName()=='java.math.BigDecimal')

const sub1 = i1 - i2
assertTrue(sub1.$getClass().getName()=='java.lang.Integer')
const sub2 = d1 - d2
assertTrue(sub2.$getClass().getName()=='java.math.BigDecimal')

const mul1 = i1 * i2
assertTrue(mul1.$getClass().getName()=='java.lang.Integer')
const mul2 = d1 * d2
assertTrue(mul2.$getClass().getName()=='java.math.BigDecimal')

const div1 = i1 / i2
assertTrue(div1.$getClass().getName()=='java.math.BigDecimal')
const div1_1 = i2 / i1
assertTrue(div1_1.$getClass().getName()=='java.lang.Integer')
const div2 = d1 / d2
assertTrue(div2.$getClass().getName()=='java.math.BigDecimal')

const mod1 = i1 & i2
assertTrue(mod1.$getClass().getName()=='java.lang.Integer')
const mod2= d1 % d2
assertTrue(mod2.$getClass().getName()=='java.math.BigDecimal')

let inc1 = d1
assertTrue(inc1.$getClass().getName()=='java.lang.Double')
inc1++
assertTrue(inc1.$getClass().getName()=='java.math.BigDecimal')

let dec1 = d1
assertTrue(dec1.$getClass().getName()=='java.lang.Double')
dec1++
assertTrue(dec1.$getClass().getName()=='java.math.BigDecimal')
