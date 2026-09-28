const $ = loadJson("store.json")

console.log('>>>> Union of three array slices\n')

const result = $.store.book[0:1,1:2,2:3];

console.log(JSON.stringify(result,null,"  "))
