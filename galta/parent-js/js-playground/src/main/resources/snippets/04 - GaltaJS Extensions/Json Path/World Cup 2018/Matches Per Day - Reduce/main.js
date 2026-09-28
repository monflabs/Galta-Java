const worldCup$ = loadJson("worldcup.json")

// Get the array of matches
const matches$ = worldCup$.rounds[*].matches[*]

// Extract an array of unique dates
const dates$ = matches$[*].date[].$distinct().sort()

// Reduce the data array and compute an object per date
const result = dates$.reduce(
	(result,date) => {
		// Calculate the list of matches for a date
		// -> given a date, filter the list of matches and add them to an array
		const matchesForDate = {
			date: date,
			matches: matches$
				.filter( (m) => m.date==date )
				.reduce( (mm,m) => {
					const s = m.team1.name + " vs " + m.team2.name
					mm.push(s);
					return mm;
				}, [])
		};
		result.push(matchesForDate);
		return result;
	}, []
)

console.log(JSON.stringify(result,null,"  "))
