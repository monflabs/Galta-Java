const $ = loadJson("store.json")

console.log('>>>> No match returns empty array\n')

const result = $..book[];

console.log(JSON.stringify(result,null,"  "))
