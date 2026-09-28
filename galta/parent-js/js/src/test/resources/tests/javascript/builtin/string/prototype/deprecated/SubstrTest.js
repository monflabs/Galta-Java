const aString = "Mozilla";

assertEquals('M', aString.substr(0, 1));
assertEquals('', aString.substr(1, 0)); 
assertEquals('a', aString.substr(-1, 1));
assertEquals('', aString.substr(1, -1));
assertEquals('lla', aString.substr(-3)); 
assertEquals('ozilla', aString.substr(1)); 
assertEquals('Mo', aString.substr(-20, 2));
assertEquals('', aString.substr(20, 2)); 