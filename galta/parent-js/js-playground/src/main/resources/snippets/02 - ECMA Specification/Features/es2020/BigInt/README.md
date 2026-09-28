# BigInt

`BigInt` represents integers of arbitrary size, beyond `Number.MAX_SAFE_INTEGER` where regular numbers start losing precision. It has its own literal suffix (`123n`), its own `typeof` ("bigint"), and arithmetic operators - but a `BigInt` and a `Number` are never strictly equal (`1n === 1` is `false`), only loosely equal (`1n == 1` is `true`).
