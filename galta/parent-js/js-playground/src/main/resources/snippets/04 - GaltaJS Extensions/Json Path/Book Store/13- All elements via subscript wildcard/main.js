const $ = loadJson("store.json")

console.log('>>>> All elements via subscript wildcard\n')

const result = $..[*];

console.log(JSON.stringify(result,null,"  "))
