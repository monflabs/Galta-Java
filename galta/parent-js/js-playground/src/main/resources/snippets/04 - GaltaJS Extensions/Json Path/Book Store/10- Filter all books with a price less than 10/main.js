const $ = loadJson("store.json")

const result = $.store.book[?(@.price < 10)]

console.log(JSON.stringify(result,null,"  "))
