/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.json.navigator;

import java.text.MessageFormat;

import org.junit.Before;
import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;
import org.monflabs.json.util.Reducers;

import tests.ProjectTestCase;

public class WorldCupQueryTest extends ProjectTestCase {
	
	private static final boolean DEBUG = false;
	
	private JsonValues worldCup;
	
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		
		worldCup = JsonValues.of(support.loadJson("json/worldcup.json"));
		if(DEBUG) {
			//worldCup = DebugJsonValues.from(worldCup);
		}
	}
	
	public void testMatchesPerDay_noreduce() throws Exception {
		// Method #1: Simple method
		
		JsonArray result = support.getJsonFactory().createArray();

		// Get the list of matches
		final JsonValues matches = worldCup
				.get("rounds").flat()
				.get("matches").flat();
		
		// Then get the list of unique dates when they happened
		final JsonValues dates = matches
				.get("date")
				.distinct();
		
		// Compose the list of matches per date
		dates.forEach( date -> {
			result.add( (JsonObject ob) -> {
				ob.put("date",date.value());
				ob.put("matches", (JsonArray match) -> {
					matches.filter( (m) -> {
						return m.getString("date").equals(date.stringValue());
					}).forEach( (m) -> {
						String s = MessageFormat.format("{0} vs {1}",
								m.getObject("team1").getString("name"),
								m.getObject("team2").getString("name")); 
						match.add(s);
					});				
				});
			});
		});
		
		// Sort the matches per date
		result.sort( (o1,o2) -> {
			Object d1 = JsonUtil.checkObject(o1).getString("date"); 
			Object d2 = JsonUtil.checkObject(o2).getString("date");
			return JsonUtil.compare(d1, d2);
		});

		if(support.isVerbose()) {
			support.print("List of matches per day");
			support.print(result.stringify(false));
		}

		support.assertJsonTemplate(result, "match-per-day");
	}

	public void testMatchesPerDayReduce() throws Exception {
		// Method #2: Use reduce

		// Get the list of matches
		final JsonValues matches = worldCup
				.getAndFlat("rounds")
				.getAndFlat("matches");
		
		// Then get the list of unique dates when they happened
		final JsonValues dates = matches
				.get("date")
				.distinct();
		
		// Compose the list of matches per date
		JsonArray result = dates.reduce( JsonArray.create(), (acc,date) -> {
			acc.add((JsonObject ob) -> {
				ob.put("date",date.value());
				ob.put("matches", (JsonArray match) -> {
					matches.filter( (m) -> {
						return m.getString("date").equals(date.stringValue());
					}).forEach( (m) -> {
						String s = MessageFormat.format("{0} vs {1}",
								m.getObject("team1").getString("name"),
								m.getObject("team2").getString("name")); 
						match.add(s);
					});				
				});
			});
			return acc;
		});
		
		// Sort the matches per date
		// JSON objects do not mandate a sort order for object, so we have to flatten the objects into an array
		result.sort( (o1,o2) -> {
			Object d1 = JsonUtil.checkObject(o1).getString("date"); 
			Object d2 = JsonUtil.checkObject(o2).getString("date");
			return JsonUtil.compare(d1, d2);
		});

		if(support.isVerbose()) {
			support.print("List of matches per day");
			support.print(result.stringify(false));
		}

		support.assertJsonTemplate(result, "match-per-day");
	}

	public void testMatchesPerDayReduce2() throws Exception {
		// Method #3: more optimized as it doesn't filter for each date
		
		// Get the list of matches
		final JsonValues matches = worldCup
				.getAndFlat("rounds")
				.getAndFlat("matches");
		
		// 1- Get the list of matches per dates
		JsonObject byDates = matches.reduce( JsonObject.create(), (dates,m) -> {
			String date = m.getString("date"); 
			JsonArray a = JsonUtil.checkObject(dates).getOrCreateArray(date,null);
			String s = MessageFormat.format("{0} vs {1}",
					m.getObject("team1").getString("name"),
					m.getObject("team2").getString("name")); 
			a.add(s);
			return dates;
		});
		
		// 2- Transform the object into an array 
		JsonArray result = JsonArray.create();
		byDates.forEach( (k,v) -> {
			result.add( (JsonObject ob) -> {
				ob.put("date",k);
				ob.put("matches", v);
			});
		});
		
		// Sort the matches per date
		// JSON objects do not mandate a sort order for object, so we have to flatten the objects into an array
		result.sort( (o1,o2) -> {
			Object d1 = JsonUtil.checkObject(o1).getString("date"); 
			Object d2 = JsonUtil.checkObject(o2).getString("date");
			return JsonUtil.compare(d1, d2);
		});

		if(support.isVerbose()) {
			support.print("List of matches per day");
			support.print(result.stringify(false));
		}

		support.assertJsonTemplate(result, "match-per-day");
	}

	public void testWinsPerTeam() throws Exception {
		JsonObject result = support.getJsonFactory().createObject();

		// Get the list of matches
		final JsonValues matches = worldCup
				.getAndFlat("rounds")
				.getAndFlat("matches");

		matches.forEach( match -> {
			int score1 = match.getInt("score1");
			int score2 = match.getInt("score2");
			String teamName1 = match.getObject("team1").getString("name");
			String teamName2 = match.getObject("team2").getString("name");
					
			addTeam(result, teamName1, score1, score2);
			addTeam(result, teamName2, score2, score1);
		});		

		if(support.isVerbose()) {
			support.print("Wins per team");
			support.print(result.stringify(false));
		}

		support.assertJsonTemplate(result, "wins-per-team");
	}
	private void addTeam(JsonObject container, String teamName, int scored, int conceded) {
		JsonObject team = container.getOrCreateObject(teamName, (JsonObject t) -> {
			t.put("wins",0).put("loss",0).put("ties",0);
		});
		if(scored>conceded) {
			team.put("wins", team.getInt("wins")+1);
		} else if(scored<conceded) {
			team.put("loss", team.getInt("loss")+1);
		} else {
			team.put("ties", team.getInt("ties")+1);
		}
	}

	public void testGoalsScoredByFrance() {
		// Get the list of matches
		final JsonValues matches = worldCup
				.getAndFlat("rounds")
				.getAndFlat("matches");
		
		int g1 = matches
				.filter( (m) -> m.getObject("team1").getString("name").equals("France") )
				.get("score1").rawReduce(Reducers.sumInt());
		int g2 = matches
				.filter( (m) -> m.getObject("team2").getString("name").equals("France") )
				.get("score2").rawReduce(Reducers.sumInt());
		int totalGoals = g1 + g2;

		if(support.isVerbose()) {
			support.print("France scored a total of {0} goals during the worldcup", totalGoals);
		}
		assertEquals(14, totalGoals);
	}

	public void testList5TopScorers() throws Exception {
		// Get the list of matches
		final JsonValues matches = worldCup
				.getAndFlat("rounds")
				.getAndFlat("matches");

		JsonArray result = matches.get("goals1","goals2").flat()
				.reduce( JsonObject.create(), (players, o) -> {
					// Only retain the last name, as some players appear sometime with their full name or only the last one
					// Ex: "Griezmann" vs "Antoine Griezmann"
					final String playerName = lastWordOf(o.getString("name").trim());
					JsonObject player = players.getOrCreateObject(playerName, (JsonObject t) -> {
						t.put("name",playerName).put("goals",0);
					});
					player.put("goals",player.getInt("goals")+1);
					return players;
				})
				.flat()
				.sorted( JsonUtil.objectComparator().add("goals",false).add("name") )
				.limit(5);

		if(support.isVerbose()) {
			support.print("Sorted list of all the players who scored at least a goal during the world cup");
			support.print(result.stringify(false));
		}

		support.assertJsonTemplate(result, "top-5-scorers");
	}
	private static String lastWordOf(String s) {
		return s.substring(s.lastIndexOf(' ')+1);
	}
}
