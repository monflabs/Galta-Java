const $ = loadJson("store.json")

console.log('>>>> Slice with step > 1\n')

const result = $.store.book[0:4:2];

console.log(JSON.stringify(result,null,"  "))
