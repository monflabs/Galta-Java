function f() {
  return 88+this.v
}

function g() {
  this.f0 = f
  this.v = 11
}

const gg = new g()

assertEquals(99,gg.f0())
assertEquals(99,gg['f0']())
