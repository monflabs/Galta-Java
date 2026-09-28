const $ = loadJson("data.json")

// All movie names
var result = $.series.movies..name
console.log("All 'series.movies.name':\n",result,"\n")

var result = $..name
console.log("All 'name':\n",result,"\n")
