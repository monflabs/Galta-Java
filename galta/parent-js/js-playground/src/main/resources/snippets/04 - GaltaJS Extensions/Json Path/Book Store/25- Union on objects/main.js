const $ = loadJson("store.json")

console.log('>>>> Union on objects\n')

const a$ = {a: 1, b: 2, c: null};
const result = a$..["a","b","c","d"];

console.log(JSON.stringify(result,null,"  "))
