# Async Iterators

Every array has a built-in synchronous iterator via `Symbol.iterator`, obtained here directly and stepped manually with `next()`, each call returning a `{ value, done }` pair until the array is exhausted. This is the underlying protocol `for...of` uses.
