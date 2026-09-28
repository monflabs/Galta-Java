const $ = loadJson("store.json")

console.log('>>>> First two books via slice\n')

const result = $..book[0:2];

console.log(JSON.stringify(result,null,"  "))
