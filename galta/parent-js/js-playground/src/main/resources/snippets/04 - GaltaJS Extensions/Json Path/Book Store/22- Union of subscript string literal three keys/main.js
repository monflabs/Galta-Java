const $ = loadJson("store.json")

console.log('>>>> Union of subscript string literal three keys\n')

const result = $.store.book[0]['title','author','price'];

console.log(JSON.stringify(result,null,"  "))
