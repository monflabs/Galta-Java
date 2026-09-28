const $ = loadJson("data.json")

// Movies rated >= 7
const result = $.series.movies[?(@.rating >= 7)]
console.log("Rating >7 :\n",result,"\n")
