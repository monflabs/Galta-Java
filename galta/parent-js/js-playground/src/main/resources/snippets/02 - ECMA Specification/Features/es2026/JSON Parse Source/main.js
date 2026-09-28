// The "id" is too large to round-trip through a JS Number without losing
// precision - the reviver's third argument, context, exposes the value's
// exact original source text so it can be kept as-is instead.
const json = '{"id": 123456789012345678901234567890, "name": "invoice"}';

const parsed = JSON.parse(json, (key, value, context) => {
  if (context.source !== undefined) {
    console.log(`"${key}" source: ${context.source}`);
  }
  return key === "id" ? context.source : value;
});

console.log(parsed.id, "(" + typeof parsed.id + ")");
console.log(parsed.name);
