# HasOwn Create

Demonstrates `Object.hasOwn()` on an object created with `Object.create(null)` (no prototype, so it has no inherited `hasOwnProperty` method at all) - `Object.hasOwn()` still works correctly.
