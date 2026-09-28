const $ = loadJson("store.json")

console.log('>>>> Member numeric literal matches string-numeric key\n')

const $$ = { authors: { '1': 'Herman Melville', '2': 'J. R. R. Tolkien' } };
const result = $$.authors.1;

console.log(JSON.stringify(result,null,"  "))
