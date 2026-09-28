# Proxies

Shows a `Proxy` with a `get` trap that returns a fallback message for missing properties, then a second proxy with a `set` trap that validates a value (throwing `TypeError`/`RangeError` on invalid input) before storing it.
