# Shared Memory Atomics

`SharedArrayBuffer` backs a typed array that multiple threads could access concurrently, and the `Atomics` object (`add`, `sub`, `and`, `or`, `xor`, `compareExchange`, `exchange`, `load`/`store`) performs read-modify-write operations on it safely without races. `Atomics.notify`/`wait` provide low-level thread coordination on top of that shared memory.
