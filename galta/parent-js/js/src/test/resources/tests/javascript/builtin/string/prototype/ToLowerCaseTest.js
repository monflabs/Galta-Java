const dotted = 'İstanbul';

assertEquals( "i̇stanbul", dotted.toLocaleLowerCase('en-US'));
assertEquals( "istanbul", dotted.toLocaleLowerCase('tr'));
