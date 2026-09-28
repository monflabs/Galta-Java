{
	const [a, b, ...rest] = [10, 20, 30, 40, 50];
	assertEquals(10, a)
	assertEquals(20, b)
	assertEquals([30,40,50], rest)
}

{
	const { a, b, ...rest } = {a:10, b:20, c:30, d:40, e:50};
	assertEquals(10, a)
	assertEquals(20, b)
	assertEquals({c:30, d:40, e:50}, rest)
}

{
	const f = function f() {
  		return [1, 2];
	}
	let [a, b] = f();
	assertEquals(a, 1)
	assertEquals(b, 2)
}

{
	const metadata = {
	  title: 'Scratchpad',
	  translations: [
	    {
	      locale: 'de',
	      localization_tags: [],
	      last_edit: '2014-04-14T08:43:37',
	      url: '/de/docs/Tools/Scratchpad',
	      title: 'JavaScript-Umgebung'
	    }
	  ],
	  url: '/en-US/docs/Tools/Scratchpad'
	};
	
	let {
	  title: englishTitle, // rename
	  translations: [
	    {
	       title: localeTitle, // rename
	    },
	  ],
	} = metadata;

	assertNotDeclared("title")
	assertEquals(englishTitle, "Scratchpad")
	assertEquals(localeTitle, "JavaScript-Umgebung")	
}

{
	const people = [
	  {
	    name: 'Mike Smith',
	    family: {
	      mother: 'Jane Smith',
	      father: 'Harry Smith',
	      sister: 'Samantha Smith'
	    },
	    age: 35
	  },
	  {
	    name: 'Tom Jones',
	    family: {
	      mother: 'Norah Jones',
	      father: 'Richard Jones',
	      brother: 'Howard Jones'
	    },
	    age: 25
	  }
	];
	
	const a = []
	for (const {name: n, family: {father: f}} of people) {
		a.push([n,f]);
	}
	
	assertEquals(['Mike Smith','Harry Smith'],a[0])
	assertEquals(['Tom Jones','Richard Jones'],a[1])
}
