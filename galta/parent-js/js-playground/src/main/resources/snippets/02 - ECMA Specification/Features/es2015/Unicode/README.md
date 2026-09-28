# Unicode

ES2015 adds `\u{...}` code point escapes and the regex `u` flag for correctly matching characters outside the Basic Multilingual Plane (like `𠮷`, which needs a UTF-16 surrogate pair). `String.prototype.codePointAt()` and `String.fromCodePoint()` work with full code points instead of individual UTF-16 code units.
