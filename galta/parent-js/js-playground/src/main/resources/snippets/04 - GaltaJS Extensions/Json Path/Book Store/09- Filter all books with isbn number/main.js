const $ = loadJson("store.json")

console.log('>>>> Filter all books with isbn number\n')

const result = $..book[?(@.isbn)];

console.log(JSON.stringify(result,null,"  "))
