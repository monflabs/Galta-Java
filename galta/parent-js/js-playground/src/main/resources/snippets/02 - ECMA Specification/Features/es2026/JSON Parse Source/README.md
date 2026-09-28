# JSON Parse Source

`JSON.parse`'s reviver now receives a third `context` argument whose `source` property carries a value's exact original JSON text - the key to preserving precision a plain `Number` would otherwise lose.
