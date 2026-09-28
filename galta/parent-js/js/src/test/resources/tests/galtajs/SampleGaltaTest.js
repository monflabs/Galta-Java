//
// The tests are borrowed and adapted from https://github.com/dchester/jsonpath
//    MIT license
//

const $ = { 
	"store": {
	    "book": [ 
	      { "category": "reference",
	        "author": "Nigel Rees",
	        "title": "Sayings of the Century",
	        "price": 8.95
	      },
	      { "category": "fiction",
	        "author": "Evelyn Waugh",
	        "title": "Sword of Honour",
	        "price": 12.99
	      },
	      { "category": "fiction",
	        "author": "Herman Melville",
	        "title": "Moby Dick",
	        "isbn": "0-553-21311-3",
	        "price": 8.99
	      },
	      { "category": "fiction",
	        "author": "J. R. R. Tolkien",
	        "title": "The Lord of the Rings",
	        "isbn": "0-395-19395-8",
	        "price": 22.99
	      }
	    ],
	    "bicycle": {
	      "color": "red",
	      "price": 19.95
	    }
     }
}
const data = $;

suite('query', function() {

  test('authors of all books in the store', function() {
    var results = $.store.book[*].author;
    assertEquals(results, [
      'Nigel Rees',
      'Evelyn Waugh',
      'Herman Melville',
      'J. R. R. Tolkien'
    ]);
  });

  test('all authors', function() {
    var results = $..author;
    assertEquals(results, [
      'Nigel Rees',
      'Evelyn Waugh',
      'Herman Melville',
      'J. R. R. Tolkien'
    ]);
  });

  test('all authors via subscript descendant string literal', function() {
    var results = $..['author'];
    assertEquals(results, [
      'Nigel Rees',
      'Evelyn Waugh',
      'Herman Melville',
      'J. R. R. Tolkien'
    ]);
  });

  test('all things in store', function() {
    var results = $.store.*;
    assertEquals(results, [
      data.store.book,
      data.store.bicycle
    ]);
  });

  test('price of everything in the store', function() {
    var results = $.store..price;
    assertEquals(results, [
      8.95,
      12.99,
      8.99,
      22.99,
      19.95
    ]);
  });

  test('last book in order via expression', function() {
    var results = $..book[(@.length-1)];
    assertEquals(results, data.store.book[3]);
  });

  test('first two books via union', function() {
    var results = $..book[0,1];
    assertEquals(results, [
      data.store.book[0],
      data.store.book[1]
    ]);
  });

  test('first two books via slice', function() {
    var results = $..book[0:2];
    assertEquals(results, [
      data.store.book[0],
      data.store.book[1]
    ]);
  });

  test('filter all books with isbn number', function() {
    var results = $..book[?(@.isbn)];
    assertEquals(results, [
      data.store.book[2],
      data.store.book[3]
    ]);
  });

  test('filter all books with a price less than 10', function() {
    var results = $..book[?(@.price<10)];
    assertEquals(results, [
      data.store.book[0],
      data.store.book[2]
    ]);
  });

  test('first of all elements', function() {
    var results = $..[0];
    assertEquals(results, data.store.book[0])
  });

  test('first ten of all elements', function() {
    var results = $..[0:10];
    assertEquals(results, [
      data.store.book[0],
      data.store.book[1],
      data.store.book[2],
      data.store.book[3],
    ])
  });
  
  test('all elements', function() {
    var results = $..*;

    assertEquals(results, [
      data.store,
      data.store.book,
      data.store.bicycle,
      data.store.book[0],
      data.store.book[1],
      data.store.book[2],
      data.store.book[3],
      'reference',
      'Nigel Rees',
      'Sayings of the Century',
      8.95,
      'fiction',
      'Evelyn Waugh',
      'Sword of Honour',
      12.99,
      'fiction',
      'Herman Melville',
      'Moby Dick',
      '0-553-21311-3',
      8.99,
      'fiction',
      'J. R. R. Tolkien',
      'The Lord of the Rings',
      '0-395-19395-8',
      22.99,
      'red',
      19.95
    ]);
  });

  test('all elements via subscript wildcard', function() {
    assertEquals( $..[*], $..*);
  });

  test('object subscript wildcard', function() {
    var results = $.store[*];
    assertEquals(results, [ data.store.book, data.store.bicycle ]);
  });

  test('no match returns empty array', function() {
    // GaltaJS will dereference a null sequence as null
    // To force it as an array, use [] 
    var results = $..bookz;
    assertEquals(results, null);
    var results2 = $..bookz[];
    assertEquals(results2, []);
  });

  test('member numeric literal gets first element', function() {
    var results = $.store.book.0;
    assertEquals(results, data.store.book[0]);
  });

  test('member numeric literal matches string-numeric key', function() {
    var $ = { authors: { '1': 'Herman Melville', '2': 'J. R. R. Tolkien' } };
    var results = $.authors.1;
    assertEquals(results, 'Herman Melville');
  });

  test('descendant numeric literal gets first element', function() {
    var results = $.store.book..0;
    assertEquals(results, data.store.book[0]);
  });

  test('root element gets us original obj', function() {
    var results = $;
    assertSame(results, data);
  });

  test('subscript double-quoted string', function() {
    var results = $["store"];
    assertEquals(results, data.store);
  });

  test('subscript single-quoted string', function() {
    var results = $['store'];
    assertEquals(results, data.store);
  });

  test('union of three array slices', function() {
    var results = $.store.book[0:1,1:2,2:3];
    assertEquals(results, data.store.book.slice(0,3));
  });

  test('slice with step > 1', function() {
    var results = $.store.book[0:4:2];
    assertEquals(results, [ 
    	data.store.book[0],
    	data.store.book[2]
    ]);
  });

  test('union of subscript string literal keys', function() {
    var results = $.store['book','bicycle'];
    assertEquals(results, [data.store.book, data.store.bicycle]);
  });

  test('union of subscript string literal three keys', function() {
    var results = $.store.book[0]['title','author','price'];
    assertEquals(results, [
    	data.store.book[0].title, 
    	data.store.book[0].author,
    	data.store.book[0].price
    ]);
  });

  test('union of subscript integer three keys followed by member-child-identifier', function() {
    var results = $.store.book[1,2,3]['title'];
    assertEquals(results, [
    	data.store.book[1].title,
    	data.store.book[2].title,
    	data.store.book[3].title
    ]);
  });

  test('union of subscript integer three keys followed by union of subscript string literal three keys', function() {
    var results = $.store.book[0,1,2,3]['title','author','price'];
    assertEquals(results, [
      data.store.book[0].title,
      data.store.book[0].author,
      data.store.book[0].price,
      data.store.book[1].title,
      data.store.book[1].author,
      data.store.book[1].price,
      data.store.book[2].title,
      data.store.book[2].author,
      data.store.book[2].price,
      data.store.book[3].title,
      data.store.book[3].author,
      data.store.book[3].price
    ]);
  });

  test('union of subscript integer four keys, including an inexistent one, followed by union of subscript string literal three keys', function() {
    var results = $.store.book[0,1,2,3,151]['title','author','price'];
    assertEquals(results, [
      data.store.book[0].title,
      data.store.book[0].author,
      data.store.book[0].price,
      data.store.book[1].title,
      data.store.book[1].author,
      data.store.book[1].price,
      data.store.book[2].title,
      data.store.book[2].author,
      data.store.book[2].price,
      data.store.book[3].title,
      data.store.book[3].author,
      data.store.book[3].price
    ]);
  });
  
  test('union of subscript integer three keys followed by union of subscript string literal three keys, followed by inexistent literal key', function() {
    var results = $.store.book[0,1,2,3]['title','author','price','fruit'];
    assertEquals(results, [
      data.store.book[0].title,
      data.store.book[0].author,
      data.store.book[0].price,
      data.store.book[1].title,
      data.store.book[1].author,
      data.store.book[1].price,
      data.store.book[2].title,
      data.store.book[2].author,
      data.store.book[2].price,
      data.store.book[3].title,
      data.store.book[3].author,
      data.store.book[3].price
    ]);
  });

  test('union of subscript 4 array slices followed by union of subscript string literal three keys', function() {
    var results = $.store.book[0:1,1:2,2:3,3:4]['title','author','price'];
    assertEquals(results, [
      data.store.book[0].title,
      data.store.book[0].author,
      data.store.book[0].price,
      data.store.book[1].title,
      data.store.book[1].author,
      data.store.book[1].price,
      data.store.book[2].title,
      data.store.book[2].author,
      data.store.book[2].price,
      data.store.book[3].title,
      data.store.book[3].author,
      data.store.book[3].price
    ]);
  });

  test('nested parentheses eval', function() {
    var results = $..book[?( @.price && (@.price + 20 || false) )]
    assertEquals(results, data.store.book);
  });

  test('descendant subscript numeric literal', function() {
    var data$ = [ 0, [ 1, 2, 3 ], [ 4, 5, 6 ] ];
    var results = data$..[0];
    assertEquals(results, [ 0, 1, 4 ]);
  });

  test('descendant subscript numeric literal', function() {
    var data$ = [ 0, 1, [ 2, 3, 4 ], [ 5, 6, 7, [ 8, 9 , 10 ] ] ];
    var results = data$..[0,1];
    assertEquals(results, [ 0, 1, 2, 3, 5, 6, 8, 9 ]);
  });

  test('union on objects', function() {
    var data$ = {a: 1, b: 2, c: null};
    var results = data$..["a","b","c","d"];
    assertEquals(results, [1, 2, null]);
  });
});