// Automatic number promotion
// int -> long -> bigint

mul(1,1)
mul(65536,65536)

mul(1L,1)
mul(4_611_686_018_427_387_904,4_611_686_018_427_387_904)




function mul(v1,v2) {
	const r = v1 * v2
	console.log(`${v1}*${v2} = ${r} [${v1.$getClass().getSimpleName()} * ${v2.$getClass().getSimpleName()} = ${r.$getClass().getSimpleName()}]`);
}
