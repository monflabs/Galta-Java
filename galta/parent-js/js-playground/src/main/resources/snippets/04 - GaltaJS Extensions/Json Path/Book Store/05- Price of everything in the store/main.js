const $ = loadJson("store.json")

console.log('>>>> Price of everything in the store\n')

const result = $.store..price;

console.log(JSON.stringify(result,null,"  "))
