// Building a regex out of user-supplied text used to mean hand-escaping
// every RegExp metacharacter yourself - RegExp.escape() does it for you.
const priceTag = "3.50";
const escaped = RegExp.escape(priceTag);
console.log(escaped);

// Escaped: "." only matches a literal dot.
const escapedPattern = new RegExp(escaped);
console.log(escapedPattern.test("Total: 3.50 due"));
console.log(escapedPattern.test("Total: 3X50 due"));

// Unescaped: "." is regex "any character", so "3X50" wrongly matches too.
const unescapedPattern = new RegExp(priceTag);
console.log(unescapedPattern.test("Total: 3X50 due"));
