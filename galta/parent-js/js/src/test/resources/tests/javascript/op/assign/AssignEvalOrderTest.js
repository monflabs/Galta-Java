// ECMAScript 13.15.2: LHS reference is resolved before RHS is evaluated
// This matters when RHS has side effects on LHS index expressions

// Simple assignment: a[i] = expr where expr modifies i
var a = [10,20,30,40,50];
var i = 1;
a[i] = a[++i] + a[++i];
// LHS resolves a[1], then RHS: a[2]+a[3] = 30+40 = 70
assertEquals(70, a[1]);
assertEquals(3, i);
assertEquals(30, a[2]);
assertEquals(40, a[3]);

// Chained assignment: lastX = x[currentRow] = expr where expr modifies currentRow
var x = [0,0,0,0,0,0,0,0,0,0];
var x0 = [0,10,20,30,40,50,60,70,80,90];
var currentRow = 2;
var lastRow = 0;
var nextRow = 4;
var lastX = 0;
lastX = x[currentRow] = (x0[currentRow] + (lastX + x[++currentRow] + x[++lastRow] + x[++nextRow])) * 0.25;
// LHS of inner assign resolves x[2], then RHS increments currentRow to 3
assertEquals(5, lastX); // (20 + 0 + 0 + 0 + 0) * 0.25 = 5
assertEquals(5, x[2]);
assertEquals(0, x[3]);
assertEquals(3, currentRow);

// Compound assignment: a[i] += expr where expr modifies i
var b = [10,20,30,40,50];
var j = 1;
b[j] += b[++j];
// LHS resolves b[1]=20, then RHS: b[2]=30, result: 20+30=50
assertEquals(50, b[1]);
assertEquals(2, j);

// Compound subtract: a[i] -= expr
var c = [10,20,30,40,50];
var k = 1;
c[k] -= c[++k];
// LHS resolves c[1]=20, then RHS: c[2]=30, result: 20-30=-10
assertEquals(-10, c[1]);
assertEquals(2, k);

// Compound multiply: a[i] *= expr
var d = [10,20,30,40,50];
var m = 1;
d[m] *= d[++m];
// LHS resolves d[1]=20, then RHS: d[2]=30, result: 20*30=600
assertEquals(600, d[1]);
assertEquals(2, m);

// Pre-increment in LHS index: a[++i] = expr
var e = [10,20,30,40,50];
var n = 1;
e[++n] = e[++n] + 100;
// LHS resolves e[2] (n becomes 2), then RHS: e[3]=40 (n becomes 3), result: 140
assertEquals(140, e[2]);
assertEquals(3, n);

// Multiple side effects - the lin_solve pattern from navier-stokes
var arr = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15];
var cr = 3;
var lr = 0;
var nr = 6;
arr[cr] = arr[++cr] + arr[++lr] + arr[++nr];
// LHS resolves arr[3]=4, then RHS: arr[4]+arr[1]+arr[7] = 5+2+8 = 15
assertEquals(15, arr[3]);
assertEquals(4, cr);
assertEquals(1, lr);
assertEquals(7, nr);

// Compound assignment with pre-increment in LHS: a[++i] -= expr
var f = [10,20,30,40,50];
var p = 1;
f[++p] -= f[++p];
// LHS resolves f[2]=30 (p becomes 2), reads old value 30, then RHS: f[3]=40 (p becomes 3), result: 30-40=-10
assertEquals(-10, f[2]);
assertEquals(3, p);
