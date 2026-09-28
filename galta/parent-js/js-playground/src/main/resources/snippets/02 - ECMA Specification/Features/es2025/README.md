# ECMAScript 2025 (ES16)

Spec: https://262.ecma-international.org/16.0/ (16th edition)

## What's new in this release
- Iterator helpers: `.map`, `.filter`, `.take`, `.drop`, `.flatMap`, `.reduce`, `.toArray`, `.forEach`, `.some`, `.every`, `.find`, `Iterator.from`
- New `Set` methods: `union`, `intersection`, `difference`, `symmetricDifference`, `isSubsetOf`, `isSupersetOf`, `isDisjointFrom`
- `Promise.try`
- `RegExp.escape`
- RegExp inline modifier groups (`(?i:...)`, `(?i-m:...)`)
- Duplicate named capturing groups across regex alternatives
- Import attributes / JSON modules (`import data from "./data.json" with { type: "json" }`)
- `Float16Array` and the `DataView` `getFloat16`/`setFloat16` methods

The examples in this folder cover most of the above.
