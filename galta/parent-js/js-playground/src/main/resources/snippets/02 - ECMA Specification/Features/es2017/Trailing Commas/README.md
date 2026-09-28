# Trailing Commas

Function declarations and calls may end their parameter/argument list with a trailing comma, which makes diffs cleaner when adding a new last parameter. A comma is only valid after at least one parameter - a bare `(,)` is still a syntax error.
