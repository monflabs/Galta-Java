// Experimental JS pipes
function m100(n) {
	return 100*n
}
function currency(n) {
	return`$${n}`
}

const m = 5 |> m100 |> currency 
console.log(m)
