# Matches Per Day

Uses JSONPath (`worldCup$.rounds[*].matches[*]`, `.$distinct().sort()`) plus plain `Array.forEach()` to group the 2018 World Cup's match data into one entry per match day.
