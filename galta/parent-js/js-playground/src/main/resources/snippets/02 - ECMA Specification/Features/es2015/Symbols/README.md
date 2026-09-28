# Symbols

`Symbol` creates a unique, non-enumerable property key: it's skipped by `for...in`, `JSON.stringify` and `Object.keys`, but still directly accessible, which makes it useful for hidden metadata or unique constants. `Symbol.for()` returns the same symbol for the same key from a global registry, while plain `Symbol()` calls are always distinct even with identical descriptions.
