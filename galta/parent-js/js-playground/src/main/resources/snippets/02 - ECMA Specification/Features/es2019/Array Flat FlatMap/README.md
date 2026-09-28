# Array Flat FlatMap

`Array.prototype.flat(depth)` flattens nested arrays up to the given depth (and removes holes), while `flatMap()` maps each element with a callback and then flattens the result by one level - equivalent to `map().flat(1)` but done in a single pass.
