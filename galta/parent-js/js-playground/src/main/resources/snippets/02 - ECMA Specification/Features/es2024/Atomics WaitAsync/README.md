# Atomics WaitAsync

Demonstrates `Atomics.waitAsync()`, which asynchronously waits on a shared `Int32Array` slot without blocking the calling thread, resolving its returned promise once `Atomics.notify()` wakes it.
