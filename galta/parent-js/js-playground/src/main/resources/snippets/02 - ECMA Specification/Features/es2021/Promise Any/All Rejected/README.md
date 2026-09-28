# All Rejected

Demonstrates `Promise.any()`'s failure path: when every input promise rejects, it rejects with an `AggregateError` whose `.errors` lists each individual rejection reason.
