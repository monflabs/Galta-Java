# WeakSet

`WeakSet` stores objects only (no primitives), holds them weakly so they can still be garbage collected, and offers just `add`, `has` and `delete` - no iteration or size, since membership can change at any time as the GC runs.
