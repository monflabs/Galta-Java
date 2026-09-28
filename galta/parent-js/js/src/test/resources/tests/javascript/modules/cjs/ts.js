var ts = {}; 
((module) => {
  function add(a,b) {
    return a+b;
  }
  module.exports = add;
})
(
  { 
    get exports() { 
		return ts; 
	}, 
    set exports(v) { 
		ts = v; 
		if (typeof module !== "undefined" && module.exports) { 
			module.exports = v; 
		} 
	} 
  }
)
