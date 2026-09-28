# JSON Improvements

ES2019 makes `JSON.parse` accept the previously-invalid U+2028/U+2029 line/paragraph separators inside strings (so any valid JSON string round-trips), and makes `JSON.stringify` emit well-formed output for lone surrogates instead of invalid UTF-16.
