# 24- Nested parentheses eval

Shows `$..book[?( @.price && (@.price + 20 || false) )]`: a filter predicate with nested parentheses and mixed `&&`/`||` logic, evaluated against each descendant `book`.
