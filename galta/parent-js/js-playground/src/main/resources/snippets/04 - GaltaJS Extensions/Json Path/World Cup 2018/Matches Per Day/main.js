const worldCup$ = loadJson("worldcup.json")

// Get the array of matches
const matches$ = worldCup$.rounds[*].matches[*]

// Extract an array of unique dates
const dates$ = matches$[*].date[].$distinct().sort()
const result = []

dates$.forEach(
	(date) => {
		const dt = {
			date: date,
			matches: (a=[]) => {
				matches$
					.filter( (m) => m.date==date )
					.forEach( (m) => {
						const s = m.team1.name + " vs " + m.team2.name
						a.push(s);
					});
				return a;
			}()
		};
		result.push(dt)
	}
)

console.log(JSON.stringify(result,null,"  "))
