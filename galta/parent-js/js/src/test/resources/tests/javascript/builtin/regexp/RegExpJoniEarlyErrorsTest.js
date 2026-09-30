// Pattern early errors: each is a SyntaxError from the RegExp constructor and,
// for a literal, when the script is parsed.
function syntaxError(source, flags) {
    assertThrows(() => new RegExp(source, flags), SyntaxError);
    assertThrows(() => eval('/' + source + '/' + flags), SyntaxError);
}
function valid(source, flags) {
    new RegExp(source, flags);
}

// Property escapes: exact names and values only (no loose matching, no
// "In"/"Is" prefix, a script needs "Script="), and only the properties
// ECMA-262 lists.
syntaxError('\\p{ Lu }', 'u');
syntaxError('\\p{lu}', 'u');
syntaxError('\\p{Lowercase_letter}', 'u');
syntaxError('\\p{InGreek}', 'u');
syntaxError('\\p{Greek}', 'u');
syntaxError('\\p{Other_Alphabetic}', 'u');
syntaxError('\\P{Hyphen}', 'u');
syntaxError('\\p{Other_Alphabetic}', 'v');
valid('\\p{Lu}', 'u');
valid('\\p{Lowercase_Letter}', 'u');
valid('\\p{Script=Greek}', 'u');
valid('\\p{sc=Grek}', 'u');
valid('\\p{Alphabetic}', 'u');
valid('\\p{ASCII}', 'u');
valid('\\p{lu}', ''); // not unicode mode: "\p" is just "p"

// A property escape cannot be a class range endpoint in unicode mode
syntaxError('[\\p{Hex}-z]', 'u');
syntaxError('[--\\p{Hex}]', 'u');
syntaxError('[\\p{Hex}--]', 'u');
valid('[\\p{Hex}-]', 'u');
valid('[\\d-z]', ''); // Annex B: "-" next to a class escape is literal

// "v" mode: syntax characters must be escaped in a class, and doubled
// punctuators are reserved
for (const c of ['(', ')', '{', '}', '/', '-', '|']) {
    syntaxError('[' + c + ']', 'v');
}
syntaxError('[a/]', 'v');
for (const c of ['!', '#', '$', '%', '*', '+', ',', '.', ':', ';', '<', '=', '>', '?', '@', '`', '~', '&']) {
    syntaxError('[' + c + c + ']', 'v');
}
syntaxError('[^^^]', 'v');
syntaxError('[_^^]', 'v');
valid('[\\(\\)\\{\\}\\/\\-\\|]', 'v');
valid('[!#]', 'v');
valid('[a&&b]', 'v');
valid('[(){}]', 'u');

// A lookbehind is never quantifiable, in any mode
syntaxError('.(?<=.)?', '');
syntaxError('.(?<!.){2,3}', '');
syntaxError('.(?<=.)*', 'u');
valid('.(?=.)?', ''); // Annex B allows a quantified lookahead without "u"
syntaxError('.(?=.)?', 'u');

// "\k" is an escape for a named backreference once the pattern has a named group
syntaxError('(?<a>.)\\k', '');
syntaxError('\\k(?<a>.)', '');
valid('\\k', ''); // no named group: "\k" is "k"
assertTrue(/\k/.test('k'));
valid('(?<a>.)\\k<a>', '');
