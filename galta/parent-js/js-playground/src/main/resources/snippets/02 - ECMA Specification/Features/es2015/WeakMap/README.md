# WeakMap

`WeakMap` keys must be objects, held weakly so they don't prevent garbage collection - and are compared strictly by reference, so a fresh `{}` never matches an existing key even if empty. This example sets and reads values, and shows `delete()` removing an entry.
