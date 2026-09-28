import "./beautify-css.js"

//const text = ".h1{ font-family: \"ff\"; }; .h2{ font-family: \"ff\"; };"
const text = loadText("css-minified.css")
const v = css_beautify(text)

console.log(v);
