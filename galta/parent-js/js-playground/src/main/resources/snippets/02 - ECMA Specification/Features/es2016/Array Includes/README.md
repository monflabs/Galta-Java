# Array Includes

`Array.prototype.includes()` tests for an element's presence and returns a boolean, unlike `indexOf()` it also correctly finds `NaN` (which `indexOf` can never match, since it uses `===`) and treats holes in sparse arrays as `undefined`.
