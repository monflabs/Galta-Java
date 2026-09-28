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
package tests.javascript.util;


import org.monflabs.galtajs.rt.RuntimeUtil;

import tests.javascript.JavaScriptStrictTestCase;

public class RuntimeUtilTest extends JavaScriptStrictTestCase {
	
    public void testtoUInt32() {
        assertEquals(RuntimeUtil.toUInt32(+0.0), 0);
        assertEquals(RuntimeUtil.toUInt32(-0.0), 0);
        assertEquals(RuntimeUtil.toUInt32(Double.NaN), 0);
        assertEquals(RuntimeUtil.toUInt32(Double.POSITIVE_INFINITY), 0);
        assertEquals(RuntimeUtil.toUInt32(Double.NEGATIVE_INFINITY), 0);
        assertEquals(RuntimeUtil.toUInt32(9223372036854775807.0d), 0);
        assertEquals(RuntimeUtil.toUInt32(-9223372036854775807.0d), 0);
        assertEquals(RuntimeUtil.toUInt32(1099511627776.0d), 0);
        assertEquals(RuntimeUtil.toUInt32(-1099511627776.0d), 0);
        assertEquals(RuntimeUtil.toUInt32(4294967295.0d), 4294967295l);
        assertEquals(RuntimeUtil.toUInt32(4294967296.0d), 0);
        assertEquals(RuntimeUtil.toUInt32(4294967297.0d), 1);
        assertEquals(RuntimeUtil.toUInt32(-4294967295.0d), 1);
        assertEquals(RuntimeUtil.toUInt32(-4294967296.0d), 0);
        assertEquals(RuntimeUtil.toUInt32(-4294967297.0d), 4294967295l);
        assertEquals(RuntimeUtil.toUInt32(4294967295.6d), 4294967295l);
        assertEquals(RuntimeUtil.toUInt32(4294967296.6d), 0);
        assertEquals(RuntimeUtil.toUInt32(4294967297.6d), 1);
        assertEquals(RuntimeUtil.toUInt32(-4294967295.6d), 1);
        assertEquals(RuntimeUtil.toUInt32(-4294967296.6d), 0);
        assertEquals(RuntimeUtil.toUInt32(-4294967297.6d), 4294967295l);
    }

    public void testToInt32() {
        assertEquals(RuntimeUtil.toInt32(+0.0), 0);
        assertEquals(RuntimeUtil.toInt32(-0.0), 0);
        assertEquals(RuntimeUtil.toInt32(Double.NaN), 0);
        assertEquals(RuntimeUtil.toInt32(Double.POSITIVE_INFINITY), 0);
        assertEquals(RuntimeUtil.toInt32(Double.NEGATIVE_INFINITY), 0);
        assertEquals(RuntimeUtil.toInt32(9223372036854775807.0d), 0);
        assertEquals(RuntimeUtil.toInt32(-9223372036854775807.0d), 0);
        assertEquals(RuntimeUtil.toInt32(1099511627776.0d), 0);
        assertEquals(RuntimeUtil.toInt32(-1099511627776.0d), 0);
        assertEquals(RuntimeUtil.toInt32(4294967295.0d), -1);
        assertEquals(RuntimeUtil.toInt32(4294967296.0d), 0);
        assertEquals(RuntimeUtil.toInt32(4294967297.0d), 1);
        assertEquals(RuntimeUtil.toInt32(-4294967295.0d), 1);
        assertEquals(RuntimeUtil.toInt32(-4294967296.0d), 0);
        assertEquals(RuntimeUtil.toInt32(-4294967297.d), -1);
        assertEquals(RuntimeUtil.toInt32(4294967295.6d), -1);
        assertEquals(RuntimeUtil.toInt32(4294967296.6d), 0);
        assertEquals(RuntimeUtil.toInt32(4294967297.6d), 1);
        assertEquals(RuntimeUtil.toInt32(-4294967295.6d), 1);
        assertEquals(RuntimeUtil.toInt32(-4294967296.6d), 0);
        assertEquals(RuntimeUtil.toInt32(-4294967297.6d), -1);

        // Object
        assertTrue(RuntimeUtil.toInt32(getEnvironment(),3) == 3);
        assertTrue(RuntimeUtil.toInt32(getEnvironment(),3.14) == 3);
        assertTrue(RuntimeUtil.toInt32(getEnvironment(),3L) == 3);
        assertTrue(RuntimeUtil.toInt32(getEnvironment(),Double.POSITIVE_INFINITY) == 0);
    }

    public void testtoUInt16() {
        assertEquals(RuntimeUtil.toUInt16(+0.0), 0);
        assertEquals(RuntimeUtil.toUInt16(-0.0), 0);
        assertEquals(RuntimeUtil.toUInt16(Double.NaN), 0);
        assertEquals(RuntimeUtil.toUInt16(Double.POSITIVE_INFINITY), 0);
        assertEquals(RuntimeUtil.toUInt16(Double.NEGATIVE_INFINITY), 0);
        assertEquals(RuntimeUtil.toUInt16(9223372036854775807.0d), 0);
        assertEquals(RuntimeUtil.toUInt16(-9223372036854775807.0d), 0);
        assertEquals(RuntimeUtil.toUInt16(1099511627776.0d), 0);
        assertEquals(RuntimeUtil.toUInt16(-1099511627776.0d), 0);
        assertEquals(RuntimeUtil.toUInt16(4294967295.0d), 65535);
        assertEquals(RuntimeUtil.toUInt16(4294967296.0d), 0);
        assertEquals(RuntimeUtil.toUInt16(4294967297.0d), 1);
        assertEquals(RuntimeUtil.toUInt16(-4294967295.0d), 1);
        assertEquals(RuntimeUtil.toUInt16(-4294967296.0d), 0);
        assertEquals(RuntimeUtil.toUInt16(-4294967297.0d), 65535);
        assertEquals(RuntimeUtil.toUInt16(4294967295.6d), 65535);
        assertEquals(RuntimeUtil.toUInt16(4294967296.6d), 0);
        assertEquals(RuntimeUtil.toUInt16(4294967297.6d), 1);
        assertEquals(RuntimeUtil.toUInt16(-4294967295.6d), 1);
        assertEquals(RuntimeUtil.toUInt16(-4294967296.6d), 0);
        assertEquals(RuntimeUtil.toUInt16(-4294967297.6d), 65535);
        
        // Object
        assertTrue(RuntimeUtil.toUInt16(getEnvironment(),3) == 3);
        assertTrue(RuntimeUtil.toUInt16(getEnvironment(),3.14) == 3);
        assertTrue(RuntimeUtil.toUInt16(getEnvironment(),Double.POSITIVE_INFINITY) == 0);
    }

}
