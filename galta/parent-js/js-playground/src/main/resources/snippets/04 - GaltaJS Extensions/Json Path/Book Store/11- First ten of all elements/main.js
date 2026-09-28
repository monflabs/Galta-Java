const $ = loadJson("store.json")

console.log('>>>> First ten of all elements\n')

const result = $..[0:10];

console.log(JSON.stringify(result,null,"  "))
