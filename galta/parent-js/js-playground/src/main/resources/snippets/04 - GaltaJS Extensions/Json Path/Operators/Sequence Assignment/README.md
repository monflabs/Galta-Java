# Sequence Assignment

Shows assigning through a path expression: `o..a = 20` sets an existing property everywhere it's found, `o.*.a = 10` sets it on every object even where it didn't previously exist, and `o..b *= 2` applies a compound assignment across every match.
