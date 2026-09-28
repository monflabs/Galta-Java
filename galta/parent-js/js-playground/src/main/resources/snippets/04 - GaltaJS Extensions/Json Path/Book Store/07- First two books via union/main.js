const $ = loadJson("store.json")

console.log('>>>> First two books via union\n')

const result = $..book[0,1];

console.log(JSON.stringify(result,null,"  "))
