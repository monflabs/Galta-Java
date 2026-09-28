const $ = loadJson("store.json")

console.log('>>>> All elements\n')

const result = $..*;

console.log(JSON.stringify(result,null,"  "))
