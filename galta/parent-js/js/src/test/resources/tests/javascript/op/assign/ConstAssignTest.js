assertParseError("const v = 14; v=1");
assertParseError("const v = 14; v+=1");
assertParseError("const v = 14; v-=1");
assertParseError("const v = 14; v*=1");
assertParseError("const v = 14; v/=1");
assertParseError("const v = 14; v%=1");

assertParseError("const v = 14; v&=1");
assertParseError("const v = 14; v|=1");
assertParseError("const v = 14; v^=1");
assertParseError("const v = 14; v&&=1");
assertParseError("const v = 14; v||=1");
assertParseError("const v = 14; v??=1");
assertParseError("const v = 14; v=1");

assertParseError("const v = 14; v++");
assertParseError("const v = 14; v--");
assertParseError("const v = 14; ++v");
assertParseError("const v = 14; --v");

assertParseError("use strict; v=1");
assertParseError("use strict; v+=1");
assertParseError("use strict; v-=1");
assertParseError("use strict; v*=1");
assertParseError("use strict; v/=1");
assertParseError("use strict; v%=1");

assertParseError("use strict; v&=1");
assertParseError("use strict; v|=1");
assertParseError("use strict; v^=1");
assertParseError("use strict; v&&=1");
assertParseError("use strict; v||=1");
assertParseError("use strict; v??=1");
assertParseError("use strict; v=1");

assertParseError("use strict; v++");
assertParseError("use strict; v--");
assertParseError("use strict; ++v");
assertParseError("use strict; --v");
