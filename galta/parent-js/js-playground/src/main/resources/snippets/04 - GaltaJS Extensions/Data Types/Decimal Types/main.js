// Decimals come in two Java types:
//  - java.math.BigDecimal: computed with the environment's MathContext (34 digits by default).
//    It has no NaN, infinities or -0: these results are the matching Numbers.
//  - org.monflabs.util.number.Decimal128: IEEE 754 decimal128 (34 digits), as in the TC39 Decimal proposal.
//    It carries its own NaN, infinities and -0.
// The m literals are BigDecimal with the GaltaJS extensions (useBigDecimal), Decimal128 otherwise.
// Either type can be created explicitly from Java.

const BigDecimal = Java.type("java.math.BigDecimal")
const Decimal128 = Java.type("org.monflabs.util.number.Decimal128")

function show(label, v) {
    console.log(`${label}: ${v} (${typeof v}, ${v.$getClass().getSimpleName()})`)
}

// The default type, from a literal
show("literal", 1.5m)

// Force one type or the other
// new returns an object, so a Java constructor gives a boxed decimal (like new Number(1)): valueOf() unboxes it
show("new BigDecimal", new BigDecimal("0.1"))
const big = new BigDecimal("0.1").valueOf()
// Decimal128 has no public constructor, only factory methods
const d128 = Decimal128.of("0.1")
show("BigDecimal", big)
show("Decimal128", d128)

// Both are exact decimals, unlike doubles
show("BigDecimal 0.1+0.2", big + new BigDecimal("0.2"))
show("Decimal128 0.1+0.2", d128 + Decimal128.of("0.2"))
show("double 0.1+0.2", 0.1 + 0.2)
show("BigDecimal 1/3", new BigDecimal("1") / new BigDecimal("3"))
show("Decimal128 1/3", Decimal128.of("1") / Decimal128.of("3"))

// Overflow: the exponent range follows the precision (up to 6144 for 34 digits)
show("BigDecimal overflow", new BigDecimal("9e6144") * new BigDecimal("10"))
show("Decimal128 overflow", Decimal128.of("9e6144") * Decimal128.of("10"))

// Special values: Numbers for BigDecimal, Decimal128 values for Decimal128
show("BigDecimal 1/0", new BigDecimal("1") / new BigDecimal("0"))
show("Decimal128 1/0", Decimal128.of("1") / Decimal128.of("0"))
show("BigDecimal 0/0", new BigDecimal("0") / new BigDecimal("0"))
show("Decimal128 0/0", Decimal128.of("0") / Decimal128.of("0"))
show("BigDecimal -1*0", new BigDecimal("-1") * new BigDecimal("0"))
show("Decimal128 -1*0", Decimal128.of("-1") * Decimal128.of("0"))
console.log("BigDecimal -1*0 is -0:", Object.is(new BigDecimal("-1") * new BigDecimal("0"), -0))
console.log("Decimal128 -1*0 is -0:", Object.is(Decimal128.of("-1") * Decimal128.of("0"), Decimal128.of("-0")))
show("BigDecimal ceil(-0.5)", Math.ceil(new BigDecimal("-0.5")))
show("Decimal128 ceil(-0.5)", Math.ceil(Decimal128.of("-0.5")))

// Mixing the two types gives a BigDecimal
show("mixed", big + d128)
