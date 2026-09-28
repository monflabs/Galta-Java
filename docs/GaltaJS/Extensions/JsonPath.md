# JSON Path

The [sequence](/GaltaJS/Extensions/Sequences) operators give GaltaJS a built-in JSON Path engine that is at the same time plain JavaScript: every query from the original JSON Path "store" example works as an expression, and the result can be piped straight into `map`, `reduce`, template literals or Java. All operators require `supportSequenceExtensions`; filters and maps also use the `@` reference (`supportIdentifierAtSign`). Both are on in `GaltaJSEnvironment`.

## Operator reference

| Syntax | Meaning | Example |
|---|---|---|
| `.name` | Member (forgiving on sequences: `null`/`undefined` items are skipped) | `$.store.bicycle.color` |
| `.0`, `.22` | Numeric member name, an index | `$.store.book.0.title` |
| `..name` | Deep scan: `name` at any depth below | `$..author` |
| `..` (bare) | Deep scan yielding the value and every descendant | `$..` then `[?( )]` |
| `.*`, `[*]` | Flatten an array (or an object's values) into a sequence | `$.store.book[*]` |
| `[i]`, `[-1]` | Index, negative counts from the end | `$..book[-1]` |
| `[i, j, ...]` | Several indexes or member names | `$..book[0,1]`, `$.store['book','bicycle']` |
| `[start:end:step]` | Slice, Python style: `end` exclusive, any part optional, negatives allowed, negative step reverses | `$..book[0:2]`, `$..book[::2]`, `a[::-1]` |
| `[?( expr )]` | Filter; `@` is the current item; `expr` may also be a function or an arrow | `$..book[?(@.price < 10)]` |
| `.( expr )` | Map: replaces each item with `expr` evaluated with `@` as the item (a function value is called with `this` = item) | `$..book[*].(@.price * 2)` |
| `[]` | Materialize the sequence as an `Array` | `$..bookz[]` |
| `?.`, `?.[]`, `?.()` | Optional chaining, standard | `$.store?.bicycle?.color` |
| `method()` | A method call broadcasts over the sequence | `$..author.toUpperCase()` |

`$` has no special meaning; it is an ordinary identifier (the examples bind the document to it, as the JSON Path specification does).

## The store example

Sample: `doc_examples/JsonPathExamples.java`

```js
const $ = {
  store: {
    book: [
      { category: 'reference', author: 'Nigel Rees',       title: 'Sayings of the Century', price: 8.95 },
      { category: 'fiction',   author: 'Evelyn Waugh',     title: 'Sword of Honour',        price: 12.99 },
      { category: 'fiction',   author: 'Herman Melville',  title: 'Moby Dick', isbn: '0-553-21311-3', price: 8.99 },
      { category: 'fiction',   author: 'J. R. R. Tolkien', title: 'The Lord of the Rings', isbn: '0-395-19395-8', price: 22.99 }
    ],
    bicycle: { color: 'red', price: 19.95 }
  }
};
```

Children and descendants (`testChildrenAndDescendants`):

```js
$.store.book[*].author   // => ['Nigel Rees', 'Evelyn Waugh', 'Herman Melville', 'J. R. R. Tolkien']
$..author                // => same
$.store..price           // => [8.95, 12.99, 8.99, 22.99, 19.95]
$.store.*[].length       // => 2   (book and bicycle)
$.store.book.0.title     // => 'Sayings of the Century'
```

Indexes and slices (`testIndexesAndSlices`):

```js
$..book[0,1].title            // => ['Sayings of the Century', 'Sword of Honour']
$..book[0:2].title            // => same
$..book[-1].title             // => 'The Lord of the Rings'
$..book[::2].title            // => ['Sayings of the Century', 'Moby Dick']
$.store.book[0,-1].price      // => [8.95, 22.99]
$.store.bicycle['color','price']   // => ['red', 19.95]
```

Filters (`testFilters`):

```js
$..book[?(@.isbn)].title                      // => ['Moby Dick', 'The Lord of the Rings']
$..book[?(@.price < 10)].title                // => ['Sayings of the Century', 'Moby Dick']
$..book[?(@.author == 'Evelyn Waugh')].title  // => 'Sword of Honour'   (single hit collapses)
$..book[?(b => b.category == 'fiction')].title // any function works as a predicate
[1,4,2,5][?(@ >= 3)]                          // => [4, 5]   (@ is the item itself for primitives)
```

Maps (`testMapOperator`):

```js
[1,2,3][*].(@ * 10)               // => [10, 20, 30]
$..book[0:2].(@.price * 2)        // => [17.9, 25.98]
$..book[0:2].author.toUpperCase() // => ['NIGEL REES', 'EVELYN WAUGH']
```

Note that `.( )` applies to a sequence: `[1,2,3].(@ * 10)` without `[*]` maps the array as one item.

## Missing paths, single hits, and `[]`

A query that matches nothing is an empty sequence and collapses to `undefined`; one match collapses to the value itself. Append `[]` to always get an `Array`:

```js
$..bookz      // => undefined
$..bookz[]    // => []
$..color      // => 'red'
$..color[]    // => ['red']
```

Sample: `doc_examples/JsonPathExamples.java` (`testMissingPathsAndArrays`)

## Updating through a path

Paths are l-values: assignment and compound assignment apply to every matched location.

```js
$..book[?(@.price > 10)].price = 9.99;
$..book[*].price               // => [8.95, 9.99, 8.99, 9.99]

$.store.book[*].price *= 2;
$.store.book[*].price          // => [17.9, 25.98, 17.98, 45.98]
```

Sample: `doc_examples/JsonPathExamples.java` (`testUpdatingThroughAPath`)

## A real example: World Cup matches per day

The test resource `json/worldcup-2018/worldcup.json` lists the 64 matches of the 2018 World Cup, grouped in rounds. Path operators extract the data; standard `Array`, `Set` and template literals shape the result.

Sample: `doc_examples/JsonPathExamples.java` (`testWorldCupMatchesPerDay`)

```js
const worldCup = JSON.parse(worldCupJson);
// All the matches, across all rounds
const matches = worldCup.rounds[*].matches[*];
// Unique, sorted dates
const dates = [...new Set(matches[*].date[])].sort();
// One entry per date with the matches played that day
const perDay = dates.map(date => ({
	date,
	matches: matches[?(@.date == date)][].map(m => `${m.team1.name} vs ${m.team2.name}`)
}));
```

```java
assertEquals(64L, list(r).get(0));                       // matches.length
assertEquals(25L, list(r).get(1));                       // dates.length
assertEquals("2018-06-14", list(r).get(2));              // perDay[0].date
assertEquals(List.of("Russia vs Saudi Arabia"), list(list(r).get(3)));
```

Result (excerpt):

```json
[
  { "date": "2018-06-14", "matches": ["Russia vs Saudi Arabia"] },
  { "date": "2018-06-15", "matches": ["Egypt vs Uruguay", "Portugal vs Spain", "Morocco vs Iran"] },
  { "date": "2018-06-16", "matches": ["France vs Australia", "Peru vs Denmark", "Argentina vs Iceland", "Croatia vs Nigeria"] }
]
```

`matches` is an `Array` (the sequence collapsed when it was assigned), so `matches[*]` re-creates a sequence and `matches[?( )]` filters it; `[]` after the filter guarantees an array for `.map`, even for a day with a single match.

## Relation to the JSON Path specification

Every query of the original storebook suite (`$.store.book[*].author`, `$..author`, `$.store.*`, `$.store..price`, `$..book[(@.length-1)]`, `$..book[0,1]`, `$..book[0:2]`, `$..book[?(@.isbn)]`, `$..book[?(@.price<10)]`, `$..*`, `$..[0]`, `$.store['book','bicycle']`, `$.store.book[0,1,2,3]['title','author','price']`...) runs unchanged; the engine's test suite (`tests/galtajs/path/JsonPathTest.js`, `SampleGaltaTest.js`) checks them. Differences from a pure JSON Path implementation:

- `[(expr)]` script expressions are ordinary JavaScript expressions in brackets (`$..book[$..book.length - 1]`); there is no separate expression language.
- Cross products: `$..book[0,2]['a','b']` yields the members of item 0 then item 2 (`[1,11,3,33]` in the test), the order that reads naturally rather than the ambiguous one in the specification.
- A missing path is `undefined`, not `[]`; use `[]` to force an array.

## Gotchas

- `.( )` and `[?( )]` operate on sequences; put `[*]` (or `..`) before them when starting from an array.
- `[]` on an `Array` variable wraps it once more; use it on sequences only.
- The `@` reference needs `supportIdentifierAtSign` (on in `GaltaJSEnvironment`); a plain environment with only `supportSequenceExtensions` still supports `[?(fn)]` with a named function.
- Array `length` is a `Long` in Java.

## Source

`parser/JSParser.jj` (`CallExpressionPart`, `ArrayIndex`, `FILTEROPEN`, `MAPEXPR`, `DDOT`), `node/ASTArrayMember.java`, `node/ASTArrayMemberFlatten.java`, `node/ASTMemberFilter.java`, `node/ASTMemberMap.java`, `node/ASTArrayMemberDeepScan.java`, `rt/JSResult.java`; tests `js/src/test/resources/tests/galtajs/path/*.js`.
