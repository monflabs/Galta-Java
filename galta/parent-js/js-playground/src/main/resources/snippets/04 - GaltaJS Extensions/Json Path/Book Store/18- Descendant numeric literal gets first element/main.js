const $ = loadJson("store.json")

console.log('>>>> Descendant numeric literal gets first element\n')

const result = $.store.book..0;

console.log(JSON.stringify(result,null,"  "))
