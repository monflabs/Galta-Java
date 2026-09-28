const $ = loadJson("store.json")

console.log('>>>> All authors via subscript descendant string literal\n')

const result = $..['author'];

console.log(JSON.stringify(result,null,"  "))
