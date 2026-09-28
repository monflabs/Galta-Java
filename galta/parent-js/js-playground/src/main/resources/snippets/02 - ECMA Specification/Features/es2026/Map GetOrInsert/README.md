# Map GetOrInsert

`getOrInsert` and `getOrInsertComputed` fetch a key's value, inserting a default (or lazily computed default) only when it's missing - one call instead of a `has`/`get`/`set` dance.
