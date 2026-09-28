# Error Cause

Demonstrates the `Error` `cause` option: when `JSON.parse` fails, a new `Error` is thrown with `{ cause: err }`, preserving the original error for the caller to inspect via `err.cause`.
