# Import Bytes

[https://github.com/tc39/proposal-import-bytes](https://github.com/tc39/proposal-import-bytes)

`import data from "./file" with { type: "bytes" }` imports any file's raw bytes as a `Uint8Array` backed by an immutable `ArrayBuffer` (`data.buffer.immutable` is `true`; `resize()`/`transfer()` throw). Alongside `json` and `text`, `bytes` is the third `type` attribute GaltaJS supports.
