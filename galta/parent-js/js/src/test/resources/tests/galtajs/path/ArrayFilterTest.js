const a$ = [
	{ a: 1, t: 'item1' },
	{ a: 2, t: 'item2' },
	{ a: 3, t: 'item3' }
]

//
// Filter Array
//
const f1 = a$[?(@.a==2)].t
assertEquals('item2',f1)

const f2 = a$[?(@.a>=2)].t
assertEquals(['item2','item3'],f2)

const f3 = a$[?(@.a<2)]?.t
assertEquals('item1',f3)

const f4 = a$[?(@.a>82)]?.t
assertEquals(null,f4)

//
// Filter Objects
//

const b$ = {
    "store": {
        "book": [
            {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            },
            {
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            }
        ]
    }
}
const fo = b$.store.book[?(@.author=='Evelyn Waugh')];
assertEquals({"category": "fiction","author": "Evelyn Waugh","title": "Sword of Honour","price": 12.99},fo)



//
// Filter as functions
//
function filter(v) {return v.a==2; } 
assertEquals('item2', a$[?(filter)].t)
assertEquals('item2', a$[?( v => v.a==2 )].t)
assertEquals(['item2','item3'], a$[?( v => v.a>=2 )].t)


//
// Assignment to filtered entries
//
const a1$ = jsonDeepClone(a$);
a1$[?(@.a>=2)].t = 'surprise'
assertEquals([{ a: 1, t: 'item1' }, { a: 2, t: 'surprise' }, { a: 3, t: 'surprise' }], a1$)

const a2$ = jsonDeepClone(a$);
a2$[?(@.a==3)].t = 'surprise'
assertEquals([{ a: 1, t: 'item1' }, { a: 2, t: 'item2' }, { a: 3, t: 'surprise' }], a2$)

const a3$ = jsonDeepClone(a$);
a3$[?(@.a==1 || @.a==3)].t = 'surprise'
assertEquals([{ a: 1, t: 'surprise' }, { a: 2, t: 'item2' }, { a: 3, t: 'surprise' }], a3$)


//
// Assign filtered entries
//
a$[?(@.a>=2)].t = "itt"
assertEquals(["itt","itt"],a$[?(@.a>=2)].t)
assertEquals([{ a: 1, t: 'item1' },{ a: 2, t: 'itt' },{ a: 3, t: 'itt' }],a$)

const aa$ = [1,2,3,4,5,6,7]
aa$[?(@>=3 && @<=6)] = 9
assertEquals([1,2,9,9,9,9,7],aa$)
