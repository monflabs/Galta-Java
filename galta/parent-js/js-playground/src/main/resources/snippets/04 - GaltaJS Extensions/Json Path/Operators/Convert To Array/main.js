const $ = loadJson("data.json")

// Convert a sequence to an array and call an array method

var result = $.series.movies..star[].$distinct().sort()
console.log("Sorted actors:\n",result,"\n")

// Change all the actors
var result = $..movies.forEach( (v) => {
	v.star += " - USA";
}) 
console.log("Actors - USA:\n",$..movies,"\n")
