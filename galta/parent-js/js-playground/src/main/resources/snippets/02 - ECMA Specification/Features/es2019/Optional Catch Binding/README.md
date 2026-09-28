# Optional Catch Binding

A `catch` clause no longer requires a bound parameter - `catch {}` is valid when the error itself isn't needed, avoiding an unused-variable binding.
