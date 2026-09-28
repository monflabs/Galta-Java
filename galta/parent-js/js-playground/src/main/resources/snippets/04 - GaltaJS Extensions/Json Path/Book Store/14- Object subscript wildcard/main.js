const $ = loadJson("store.json")

console.log('>>>> Object subscript wildcard\n')

const result = $.store[*];

console.log(JSON.stringify(result,null,"  "))
