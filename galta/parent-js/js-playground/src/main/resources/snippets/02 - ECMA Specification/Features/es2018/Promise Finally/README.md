# Promise Finally

`.finally()` runs a callback once a Promise settles, whether it resolved or rejected - useful for cleanup like clearing a loading flag, without duplicating that logic in both `.then()` and `.catch()`.
