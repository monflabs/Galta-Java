const $ = loadJson("data.json")

// Slices of movies.

// First one
var result = $.series.movies[0]
console.log("First:\n",result,"\n")

// Last one
var result = $.series.movies[-1]
console.log("Last:\n",result,"\n")

// First and last
var result = $.series.movies[0,-1]
console.log("First & Last:\n",result,"\n")

// First two
var result = $.series.movies[0:2]
console.log("First Two:\n",result,"\n")

// Use an increment of 2
var result = $.series.movies[0:4:2]
console.log("With an increment of Two:\n",result,"\n")
var result = $.series.movies[:2]
console.log("With an increment of Two:\n",result,"\n")

// Reverse order
var result = $.series.movies[-1::-1]
console.log("Reverse Order:\n",result,"\n")
