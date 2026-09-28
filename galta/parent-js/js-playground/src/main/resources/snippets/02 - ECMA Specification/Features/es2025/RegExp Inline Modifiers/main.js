// (?i:...) turns case-insensitive matching ON for just that group, without
// the "i" flag affecting the rest of the pattern.
const pattern = /^(?i:hello) world$/;
console.log(pattern.test("hello world"));
console.log(pattern.test("HELLO world"));
console.log(pattern.test("HELLO WORLD"));   // "world" outside the group stays case-sensitive

// (?-i:...) does the opposite: turns case-insensitivity OFF locally, even
// under a global "i" flag.
const mixed = /^(?-i:ID)-\d+$/i;
console.log(mixed.test("ID-42"));
console.log(mixed.test("id-42"));
