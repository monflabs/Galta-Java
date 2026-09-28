// A number this large loses precision once parsed into a JS Number -
// JSON.rawJSON lets JSON.stringify emit its exact original text instead.
const preciseId = JSON.rawJSON("123456789012345678901234567890");

console.log(JSON.isRawJSON(preciseId));
console.log(JSON.isRawJSON({}));

const payload = { name: "invoice", id: preciseId };
console.log(JSON.stringify(payload));

// Compare: storing it as a plain Number first would have rounded it.
console.log(JSON.stringify({ name: "invoice", id: 123456789012345678901234567890 }));
