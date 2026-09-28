// ----------------------------------------------
function TestCase(title, expr, expected, evaluated) {
  this.title = title;
  this.expr = expr;
  this.expected = expected;
  this.evaluated = evaluated;
  if(!global.__TESTS__) {
    global.__TESTS__ = []
  }
  global.__TESTS__.push(this)
}
function test() {
  for(const t of global.__TESTS__) {
    const r = eval(t.expr)
    if(r!=t.expected) {
      console.log(`FAILED: ${t.evaluated}`)
    }
  }
}
// ----------------------------------------------
