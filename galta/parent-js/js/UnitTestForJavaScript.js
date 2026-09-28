//
// JS test support
//
function assertEquals(a, b) {
    if (typeof a === typeof b) {
	    if (isNumber(a) || isNumber(b)) {
	      if (isNaN(a) && isNaN(b)) {
	        return true;
          }
	    }
	    if (isObject(a) && isObject(b)) {
	        if(deepEqual(a, b)) {
            return true;
          }
	    }
		if (a === b) {
			return true;
		}
	}
    fail(`assertEquals failed. Expected ${a}, got ${b}`);
}

function deepEqual(object1, object2) {
    if(object1===object2) {
      return true;
    }
    const objKeys1 = Object.keys(object1);
    const objKeys2 = Object.keys(object2);
    if (objKeys1.length !== objKeys2.length) return false;
    for (var key of objKeys1) {
        const v1 = object1[key];
        const v2 = object2[key];
        if (isObject(v1) && isObject(v2)) {
            if (!deepEqual(v1, v2)) return false;
        } else {
            if (v1 !== v2) return false;
        }
    }
    return true;
}
function isObject(object) {
    return object != null && typeof object === "object";
}
function isNumber(object) {
    return object != null && typeof object === "number";
}

function assertEqualsStrict(a, b) {
    if (a === b) {
        return true;
    }
    fail(`assertEqualsStrict failed. Expected ${a}, got ${b}`);
}

function assertSame(a, b) {
    if (isObject(a) && isObject(b) && a === b) {
        return true;
    }
    fail(`assertEqualsStrict failed. Expected ${a}, got ${b}`);
}

function assertTrue(a) {
    if (a) {
        return true;
    }
    fail(`assertTrue failed. Got ${a}`);
}

function assertFalse(a) {
    if (!a) {
        return true;
    }
    fail(`assertFalse failed. Got ${a}`);
}


function assertNull(a) {
    if (a === null) {
        return true;
    }
    fail(`assertNull failed. Got ${a}`);
}

function assertNotNull(a) {
    if (a !== null) {
        return true;
    }
    fail(`assertNotNull failed. Got ${a}`);
}

function assertUndefined(a) {
    if (a === undefined) {
        return true;
    }
    fail(`assertUndefined failed. Got ${a}`);
}

function assertNotUndefined(a) {
    if (a !== undefined) {
        return true;
    }
    fail(`assertNotUndefined failed. Got ${a}`);
}

function assertNullOrUndefined(a) {
    if (a === null || a === undefined) {
        return true;
    }
    fail(`assertNullOrUndefined failed. Got ${a}`);
}

function assertNotNullOrUndefined(a) {
    if (a !== null && a !== undefined) {
        return true;
    }
    fail(`assertNotNullOrUndefined failed. Got ${a}`);
}

function assertThrows(e, f) {
    try {
        f();
    } catch (ex) {
        if (!(ex instanceof e)) {
            fail(`assertThrows failed. Exception ${ex} thrown instead of ${e}`);
        }
        return true;
    }
    fail(`assertThrows failed. No exception is thrown`);
}

function _n(s) {
    if (s != null && s.indexOf('\r') >= 0) {
        s = s.replace(/\n\r/g, '\n');
        s = s.replace(/\r\n/g, '\n');
        s = s.replace(/\r/g, '\n');
    }
    return s;
}

function fail(msg) {
    const err = new Error(msg);
    const stack = err.stack;
    if (stack) {
        const lines = stack.split("\n");
        //console.log(lines)
        if (lines.length < 3) {
            return null;
        }
        //const callLine = lines[0].trim();
        const callLine = lines.join('  \n');
        throw new Error(`${msg} at ${callLine}`);
    }
    throw err;
}
