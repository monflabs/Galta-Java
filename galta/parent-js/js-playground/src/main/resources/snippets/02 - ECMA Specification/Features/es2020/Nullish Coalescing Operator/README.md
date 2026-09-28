# Nullish Coalescing Operator

Contrasts `||` with `??`: `||` falls through on any falsy value (`""`, `0`), while `??` only falls through on `null`/`undefined`, letting legitimate falsy values like an empty string or zero survive.
