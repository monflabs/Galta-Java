// obj is an object published from a sample library
// we access its fields here

var i1=0; 
do {
	i1++
} while(i1<5)
assertEquals(5,i1)

var i2=0; 
do {
	i2++; 
	continue;
} while(i2<5)
assertEquals(5,i2)

var i3=0; 
do {
	i3++; 
	break; 
} while(i3<5)
assertEquals(1,i3)

var i4=0; 
loop: 
do {
	i4++; 
	continue loop;
} while(i4<5)
assertEquals(5,i4)

var i5=0; 
loop: do {
	i5++; 
	break loop; 
}while(i5<5)
assertEquals(1,i5)
