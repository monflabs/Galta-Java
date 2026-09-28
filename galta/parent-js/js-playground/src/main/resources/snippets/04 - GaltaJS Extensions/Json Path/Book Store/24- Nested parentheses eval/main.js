const $ = loadJson("store.json")

console.log('>>>> Nested parentheses eval\n')

const result = $..book[?( @.price && (@.price + 20 || false) )];

console.log(JSON.stringify(result,null,"  "))
