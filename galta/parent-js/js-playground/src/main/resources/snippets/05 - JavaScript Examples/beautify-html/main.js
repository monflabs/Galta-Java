// Needed by beautify-html 
import "./beautify.js"
import "./beautify-css.js"

import "./beautify-html.js"

const text = loadText("html-minified.html")
//const text = "<html><body><p>some text</p></body></html>";
const r = html_beautify(text);

console.log(r);
