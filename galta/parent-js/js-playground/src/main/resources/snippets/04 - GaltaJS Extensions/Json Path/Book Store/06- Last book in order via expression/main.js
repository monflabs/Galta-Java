const $ = loadJson("store.json")

console.log('>>>> Last book in order via expression\n')

const result = $..book[(@.length-1)];

console.log(JSON.stringify(result,null,"  "))
