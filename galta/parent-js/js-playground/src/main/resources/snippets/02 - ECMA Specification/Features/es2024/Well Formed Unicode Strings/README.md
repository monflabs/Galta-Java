# Well Formed Unicode Strings

Demonstrates `String.prototype.isWellFormed()` and `toWellFormed()`, which detect and repair lone (unpaired) UTF-16 surrogates - useful before passing a string to APIs like `encodeURI()` that throw on ill-formed input.
