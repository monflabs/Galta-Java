// Re-exports a source-phase import: resolves to the target's Module Source
// Object (spec ResolveExport's ~source~ binding), the target itself is never
// evaluated.
import source x from "native-lib";
export { x };
