import "./beautify.js"

const text = loadText("javascript-minified.js")
//const text = "function() { const a=3; b=2; return a+b; }";
const r = js_beautify(text);

console.log(r);
