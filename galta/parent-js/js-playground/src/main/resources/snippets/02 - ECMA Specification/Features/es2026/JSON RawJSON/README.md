# JSON RawJSON

`JSON.rawJSON()` wraps a piece of literal JSON text so `JSON.stringify()` emits it verbatim - useful for numbers too large or too precise to round-trip through a JS `Number`. `JSON.isRawJSON()` recognizes such a wrapper.
