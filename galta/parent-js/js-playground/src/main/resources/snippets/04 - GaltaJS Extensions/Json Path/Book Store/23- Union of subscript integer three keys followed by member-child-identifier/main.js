const $ = loadJson("store.json")

console.log('>>>> Union of subscript integer three keys followed by member-child-identifier\n')

const result = $.store.book[1,2,3]['title'];

console.log(JSON.stringify(result,null,"  "))
