const $ = loadJson("store.json")

console.log('>>>> Union of subscript string literal keys\n')

const result = $.store['book','bicycle'];

console.log(JSON.stringify(result,null,"  "))
