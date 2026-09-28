# HasOwn Overwritten

Demonstrates `Object.hasOwn()` on an object whose own `hasOwnProperty` method has been overwritten to always return `false` - `Object.hasOwn()` isn't fooled, since it doesn't call the object's own method.
