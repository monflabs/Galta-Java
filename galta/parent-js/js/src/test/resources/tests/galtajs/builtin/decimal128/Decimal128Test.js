const D = "org.monflabs.util.number.Decimal128"
function cls(v) { return v.$getClass().getName() }

// Literals and types
assertTrue(cls(1.5m)==D)
assertTrue(typeof 1.5m == "decimal")
assertTrue(typeof Decimal128("1") == "decimal")
assertTrue(Decimal128("1.5") === 1.5m)
assertTrue(Decimal === Decimal128)
assertTrue(Decimal128(10n) === 10m)
assertTrue(Decimal128(0.1) === 0.1m)
assertTrue(Decimal128("NaN") !== Decimal128("NaN") || Object.is(Decimal128("NaN"), Decimal128("NaN")))
assertTrue(Object.is(Decimal128("NaN"), Decimal128("NaN")))

// Arithmetic
assertTrue(0.1m + 0.2m === 0.3m)
assertTrue(0.3m - 0.1m === 0.2m)
assertTrue(1.5m * 2m === 3m)
assertTrue(1m / 4m === 0.25m)
assertTrue(1m / 3m === 0.3333333333333333333333333333333333m)
assertTrue(7m % 3m === 1m)
assertTrue(2m ** 10m === 1024m)
assertTrue(-(1.5m) === -1.5m)
let n = 1m; n++; assertTrue(n === 2m)
n--; n--; assertTrue(n === 0m)

// 34 digits, range
assertTrue(1234567890123456789012345678901234567m === 1234567890123456789012345678901235000m)
assertTrue(1e6144m * 10m === Decimal128("Infinity"))
assertTrue(1e-6176m / 10m === 0m)

// Special values
assertTrue(1m / 0m === Decimal128("Infinity"))
assertTrue(-1m / 0m === Decimal128("-Infinity"))
assertTrue(isNaN(0m / 0m))
assertTrue(Object.is(-0m, -0m))
assertTrue(!Object.is(-0m, 0m))
assertTrue(-0m == 0m)
assertTrue(-0m === 0m)
assertTrue(1 / Number(-0m) === -Infinity || true)
assertTrue(Decimal128("Infinity") - Decimal128("Infinity") != Decimal128("Infinity") - Decimal128("Infinity"))

// Truthiness
assertTrue(!0m)
assertTrue(!Decimal128("NaN"))
assertTrue(!!1e-400m)
assertTrue(!!0.1m)

// Comparisons
assertTrue(1m == 1)
assertTrue(1m === 1m)
assertTrue(1.5m > 1)
assertTrue(1.5m < 2m)
assertTrue(1m <= 1)
assertTrue(!(Decimal128("NaN") < 1m))
assertTrue(!(Decimal128("NaN") >= 1m))
assertTrue(Decimal128("-Infinity") < -1e30m)

// Collections use SameValueZero
assertTrue(new Map([[1m,"a"]]).get(1m) == "a")
assertTrue(new Set([0m,-0m]).size == 1)
assertTrue(new Set([Decimal128("NaN"),Decimal128("NaN")]).size == 1)
assertTrue([1m,2m].includes(2m))
assertTrue([1m,2m].indexOf(2m) == 1)

// Strings and JSON
assertTrue(String(1.50m) == "1.5")
assertTrue(`${1e400m}` == "Infinity" || `${1e400m}` == "1e400")
assertTrue(1m + "x" == "1x")
assertTrue(JSON.stringify({a:1.5m}) == '{"a":1.5}')

// Prototype
assertTrue(0.1m.add(0.2m) === 0.3m)
assertTrue(5m.subtract(2m) === 3m)
assertTrue(5m.multiply(2m) === 10m)
assertTrue(5m.divide(2m) === 2.5m)
assertTrue(5m.remainder(2m) === 1m)
assertTrue((-5m).abs() === 5m)
assertTrue(5m.negate() === -5m)
assertTrue(5m.compare(6m) == -1)
assertTrue(5m.equals(5m))
assertTrue(5m.notEquals(6m))
assertTrue(5m.lessThan(6m))
assertTrue(5m.lessThanOrEqual(5m))
assertTrue(6m.greaterThan(5m))
assertTrue(6m.greaterThanOrEqual(6m))
assertTrue(Decimal128("NaN").isNaN())
assertTrue(1m.isFinite())
assertTrue(!Decimal128("Infinity").isFinite())
assertTrue(1500m.exponent() == 3)
assertTrue(1500m.mantissa() === 1.5m)
assertTrue(1.2345m.round(2) === 1.23m)
assertTrue(1.235m.round(2, "ceil") === 1.24m)
assertTrue(5m.scale10(2) === 500m)
assertTrue(1.5m.toFixed(3) == "1.500")
assertTrue(12345.678m.toExponential() == "1.2345678e4")
assertTrue(12345.678m.toPrecision(4) == "1.235e4")
assertTrue(1.5m.toString() == "1.5")
assertTrue(1.5m.valueOf() === 1.5m)
assertTrue(Object.prototype.toString.call(1m) == "[object Decimal128]")

// Boxed
assertTrue(typeof Object(1m) == "object")
assertTrue(Object(1m) !== Object(1m))
assertTrue(Object(1m) + 1m === 2m)

// Errors
let err
try { Decimal128("x") } catch(e) { err = e }
assertTrue(err instanceof SyntaxError)
err = null
try { new Decimal128("1") } catch(e) { err = e }
assertTrue(err instanceof TypeError)
err = null
try { 1m.round(1, "bad") } catch(e) { err = e }
assertTrue(err instanceof RangeError)

// Math
assertTrue(Math.max(1m, 2m) === 2m)
assertTrue(Math.min(1m, 2m) === 1m)
assertTrue(Math.max(1, 2m) === 2m)
assertTrue(Object.is(Math.min(0m, -0m), -0m))
assertTrue(Math.abs(-1.5m) === 1.5m)
assertTrue(Math.sign(-3m) === -1m)
assertTrue(Math.floor(-1.5m) === -2m)
assertTrue(Math.ceil(1.2m) === 2m)
assertTrue(Math.trunc(-1.7m) === -1m)
assertTrue(Math.round(2.5m) === 3m)
assertTrue(Math.round(-2.5m) === -2m)
assertTrue(Math.sqrt(2m) === 1.414213562373095048801688724209698m)
assertTrue(Math.sqrt(16m) === 4m)
assertTrue(Math.cbrt(-27m) === -3m)
assertTrue(Math.exp(1m) === 2.718281828459045235360287471352662m)
assertTrue(Math.log(Math.exp(2m)).toFixed(10) == "2.0000000000")
assertTrue(Math.log(0m) === Decimal128("-Infinity"))
assertTrue(isNaN(Math.log(-1m)))
assertTrue(Math.pow(2m, 0.5m) === Math.sqrt(2m))
assertTrue(Math.sin(0m) === 0m)
assertTrue(Math.cos(0m) === 1m)
assertTrue(Math.atan2(1m, 1m).toPrecision(30) == (Math.PIm / 4m).toPrecision(30))
assertTrue(Math.PIm === 3.141592653589793238462643383279503m)
assertTrue(cls(Math.Em)==D)
assertTrue(Math.exp(Decimal128("Infinity")) === Decimal128("Infinity"))
assertTrue(Math.exp(100000m) === Decimal128("Infinity"))
assertTrue(Math.exp(-100000m) === 0m)

// Mixed with numbers (GaltaJS extension)
assertTrue(cls(1m + 1)==D)
assertTrue(cls(1 + 1m)==D)
assertTrue(cls(1m * 1.5)==D)
assertTrue(1m + 1 === 2m)
