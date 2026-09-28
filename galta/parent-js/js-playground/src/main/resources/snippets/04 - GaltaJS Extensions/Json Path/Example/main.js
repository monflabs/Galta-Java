const $ = loadJson("store.json")

console.log('\n>>>> This is an example to test your json paths!')

const result = $.store;

console.log(JSON.stringify(result,null,"  "))

