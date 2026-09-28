var i1=0; 
while(i1<5) {
	i1++
}
assertEquals(5,i1)

var i2=0; 
while(i2<5) {
	i2++; 
	continue; 
}
assertEquals(5,i2)

var i3=0; 
while(i3<5) {
	i3++; 
	break; 
}
assertEquals(1,i3)

var i4=0; 
loop: while(i4<5) {
	i4++; 
	continue loop; 
}
assertEquals(5,i4)

var i5=0; 
loop: while(i5<5) {
	i5++; 
	break loop; 
}
assertEquals(1,i5)

var i6=0; 
loop: while(i6<5) {
	i6++; 
	while(i6<15) {
		i6++
	}
}
assertEquals(15,i6)

var i7=0; 
loop: while(i7<5) {
	i7++; 
	while(i7<15) {
		i7++
		break;
	}
}
assertEquals(6,i7)
