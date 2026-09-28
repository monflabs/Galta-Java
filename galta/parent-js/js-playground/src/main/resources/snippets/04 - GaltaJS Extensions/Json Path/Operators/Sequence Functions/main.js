const $ = loadJson("data.json")

// Call a function on all the sequence elements

var result = $.series.movies..star.toUpperCase()

console.log("Actor, uppercase:\n",result,"\n")
