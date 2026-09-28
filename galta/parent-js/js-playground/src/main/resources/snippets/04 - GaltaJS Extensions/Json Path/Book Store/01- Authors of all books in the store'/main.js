const $ = loadJson("store.json")

console.log('>>>> Authors of all books in the store\n')

const result = $.store.book[*].author;

console.log(JSON.stringify(result,null,"  "))
