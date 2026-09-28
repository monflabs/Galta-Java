const $ = loadJson("data.json")

console.log("Initial doc:\n",$,"\n")

// One level of flatenization
var result = $.*;
console.log("One Level:\n",result,"\n")

// Two level2 of flatenization
var result = $.*.*;
console.log("Two Levels:\n",result,"\n")

// Alternate [] syntax
var result = $[*];
console.log("One Level, alternate:\n",result,"\n")
