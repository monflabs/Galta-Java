# Parsing

`JsonFactory` parses JSON text; `JsonObject.parse()` and `JsonArray.parse()` are shortcuts.
The default parser is lenient (unquoted names, single quotes, comments); `parse(text, true)`
is strict.
