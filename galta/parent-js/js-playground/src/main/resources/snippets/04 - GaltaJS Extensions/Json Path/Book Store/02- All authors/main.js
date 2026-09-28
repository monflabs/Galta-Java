const $ = loadJson("store.json")

console.log('>>>> All authors\n')

const result = $..author;

console.log(JSON.stringify(result,null,"  "))
