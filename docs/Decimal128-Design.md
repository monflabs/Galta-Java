# Decimal128 vs BigDecimal: design decision

Status: **decided and implemented** in `org.monflabs.util.number` (utilities); the JSON integration (`DEC128`) is done, the GaltaJS engine integration is done (`useDecimal128`).

## Goal

GaltaJS must be 100% compatible with the ECMAScript Decimal proposal
(<https://tc39.es/proposal-decimal/>) while being well integrated with Java (JDBC, Jackson, the JSON library).

The proposal's `Decimal128` is an IEEE 754 decimal128 value:

- at most 34 significant digits, exponent range -6176..6111 (adjusted exponent at most 6144);
- `NaN`, `+Infinity`, `-Infinity` and `-0` are values;
- no cohorts: `1.0m` and `1m` are the same value;
- results round half-even to 34 digits, overflow to Infinity, and underflow to zero (keeping the sign).

`java.math.BigDecimal` has unlimited precision and a scale-sensitive `equals` (`1.0 != 1`), and cannot represent
NaN, Infinity or -0.

## Observation: the arithmetic is the same either way

Whatever the representation, we must write: rounding to 34 digits, range checks, overflow, subnormals, the sign of
zero, the NaN/Infinity algebra (`Inf - Inf = NaN`, `0 / 0 = NaN`, `-0 + -0 = -0`...), and `toString`, `toFixed`,
`toPrecision`. The decision is only about the **representation** and where the conversions happen.

## Option A: BigDecimal with special singletons

Finite values stay plain `BigDecimal`. The four specials are singleton instances of a `BigDecimal` subclass:

| Value | Underlying value | Notes |
|---|---|---|
| `-0` | `0` | `compareTo`, `equals` and `hashCode` behave as 0; `toString()` returns `"-0"` |
| `+Infinity` | `1E+6145` | above every finite Decimal128, so it sorts correctly |
| `-Infinity` | `-1E+6145` | |
| `NaN` | `1E+6146` | sorts last, like `Double.compare`; `equals(NaN)` is true so collections work |

Overrides would include `doubleValue()` (NaN / ±Infinity), `toString()`, narrowing `intValue()` / `longValue()`
like `double`, fail-fast `unscaledValue()` / `toBigInteger()` for NaN and Infinity, and `readResolve`.

Strengths:

- finite values are ordinary `BigDecimal`: JDBC, Jackson and Spring need no change;
- no new numeric type in the JSON module (`BIGDEC` already flows everywhere).

Weaknesses:

1. **Silent wrong results.** `BigDecimal` methods read the *argument's* private fields directly, so overrides are
   bypassed: `plain.add(inf)` returns a huge finite number, `inf.subtract(inf)` returns `0` (not NaN), and
   `NaN.add(1)` returns a huge finite number. All arithmetic would have to go through our own static operations, and
   any Java code doing `BigDecimal` math on a possibly-special value is a bug that nothing flags.
2. **No invariants.** Any Java code can create a 50-digit or `1E+9000` `BigDecimal`. The engine would have to
   re-validate (precision, range) on every operation, or normalize on every entry point.
3. **Two meanings in one type.** An arbitrary-precision Java decimal and a bounded JS decimal would share a type, so
   the lossy step (rounding, overflow to Infinity) happens implicitly the first time a value flows in.
4. **Reliance on undocumented JDK internals** (which fields the methods read) for the sort order and leakage
   behaviour.
5. **Cost.** A 34-digit `BigDecimal` exceeds the compact `long` form: each value is a `BigDecimal`, a `BigInteger`
   and an array.
6. Specials remain unusable on every wire (JDBC, JSON): `1E+6146` stored in a database is wrong data, not an error,
   unless the accessors are made to throw, which breaks generic consumers.

## Option B: dedicated Decimal128 class (chosen)

`org.monflabs.util.number.Decimal128`: an immutable `Number` holding the IEEE 754 BID encoding in two `long`s, with
canonical encoding, exact `toBigDecimal()` / `toBigInteger()`, rounding-aware `of(BigDecimal)`, `of(String|long|double)`,
and the operations of the proposal.

Strengths:

1. **Invariants enforced by the type.** Every instance is a valid Decimal128.
2. **No silent wrongness.** Misuse is a compile error or an explicit conversion.
3. **Honest semantics.** The lossy conversion BigDecimal -> Decimal128 (round to 34 digits, overflow to Infinity) is
   explicit and testable; `BigDecimal` keeps its arbitrary-precision meaning for Java users (the `BIGDEC` option and
   `getBigDecimal` stay unchanged).
4. **Storage and speed (expected, to be measured).** 16 bytes of payload, no `BigInteger` or `int[]`.
5. **Standard wire format.** BID is the layout other systems use (as far as we know, MongoDB's BSON Decimal128 is the
   same encoding, so conversion needs no text round trip).
6. **No dependence on JDK internals.**
7. **Maps directly onto the spec**, so conformance tests translate one-to-one.

Weaknesses and mitigations:

- *Java libraries don't know it.* The places that matter are ours:
  - script-to-Java calls use the `ClassMetadata` conversion chain (a `Decimal128` passed to a `BigDecimal` parameter
    converts, and back), already designed for this;
  - the Jackson module (`GaltaJsonModule`) and the JDBC modules get one adapter each; JDBC conversion fails clearly
    on NaN/Infinity;
  - `Decimal128 extends Number`, so generic `doubleValue()` / `toString()` consumers keep working.
- *API duplication in the JSON module.* A first attempt duplicated every `BigDecimal` getter, which was heavy. The
  implementation will stay minimal (below).

## Decision

**Option B.** `Decimal128` is the JS-spec value type; `BigDecimal` remains the Java arbitrary-precision type; they
meet at explicit, tested boundaries.

Option A would only be the better choice for a mostly-Java application round-tripping decimals through JDBC where
NaN/Infinity/-0 are rare edge cases and few scripts rely on Decimal128 semantics. That is not this project's goal.

## Implementation plan

1. **utilities**: `org.monflabs.util.number.Decimal128` and `DecimalRoundingMode`. Randomized tests cross-check
   against `BigDecimal` with `MathContext(34, HALF_EVEN)`, plus the proposal's edge cases (special values, subnormals,
   `toString` plain/exponential thresholds, canonical encoding, shortest-digits `of(double)`).
2. **utilities conversions**: `TypeUtil.toDecimal128`, and `Decimal128` cases in `toBigDecimal` / `toBigInteger`;
   a `Decimal128` target in `ClassMetadata`.
3. **json module**, kept minimal:
   - `DEC128` in `JsonFactory.DECIMAL` and `OVERFLOW_DECIMAL` (default: `Decimal128.of(literal)`; as overflow type:
     used when a double cannot hold the literal and the result is finite and non-zero);
   - parser fast path unchanged when the overflow type is not `DOUBLE`-only (existing rule extended);
   - stringifier: finite values via `toString()`, NaN and ±Infinity as `null` (as for doubles);
   - `JsonUtil`: `isNaN` / `isInfinite`, `asBoolean`, `compareNumber`, `eqNumber`, `hashNumber` (consistent across
     number types), and the JSONPath comparison operators;
   - `JsonValues` accessors and `Reducers.sumDecimal128()`; other typed access through the generic
     `ClassMetadata` conversion rather than a getter for every `BigDecimal` method.
4. **adapters**: Jackson serializer/deserializer; JDBC helper.
5. **GaltaJS**: engine type, operators and `Decimal128` global per the proposal, with the existing test262 suites for
   the proposal where available.
6. **docs**: `docs/GaltaJSON/Parsing.md`, `CHANGELOG.md`, module `CLAUDE.md` files.

## Open questions

- Should `-0` stringify as `-0` or `0` in JSON output?
- Mixed arithmetic with `Number`/`BigInt`: follow the proposal's current text (to be re-checked when implementing).
- Final spec details change while the proposal is at its current stage; re-read the proposal text before the engine
  work.
