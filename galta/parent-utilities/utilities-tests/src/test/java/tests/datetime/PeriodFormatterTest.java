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
package tests.datetime;

import org.monflabs.util.datetime.PeriodFormatter;

import tests.ProjectTestCase;

public class PeriodFormatterTest extends ProjectTestCase {
	
    public void testPeriodFormatter() throws Exception {
    	assertEquals("0", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("0")));
    	assertEquals("215ms", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("215")));
    	assertEquals("1s", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1s")));
    	assertEquals("1m", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("60s")));
        assertEquals("1m1s", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1m 1s")));
      	assertEquals("2m", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1m 60s")));
        assertEquals("1h", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("60m")));
        assertEquals("1h", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1h")));
        assertEquals("1h10m", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("70m")));
        assertEquals("1D", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("24h")));
        assertEquals("1D", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1d")));
        assertEquals("1D5h54m13s", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1d 5h 54m 13s")));
        assertEquals("1W", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1w")));
        assertEquals("1W", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("7d")));
        assertEquals("1M", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1n")));
        assertEquals("1M", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("30d")));
        assertEquals("1Y", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("1y")));
        assertEquals("1Y", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("365d")));
    }

    public void testMillisecondsRoundTrip() throws Exception {
    	// formatPeriod() writes "ms"; parsePeriod() used to read it as minutes
    	assertEquals(215, PeriodFormatter.parsePeriod("215ms"));
    	assertEquals("215ms", PeriodFormatter.formatPeriod(PeriodFormatter.parsePeriod("215ms")));
    	assertEquals(60_000+215, PeriodFormatter.parsePeriod("1m215ms"));
    	assertEquals(2*60_000, PeriodFormatter.parsePeriod("2m"));
    }

	public void testInvalidPeriods() throws Exception {
		// The javadoc promises -1 for invalid input: only "" used to return it
		assertEquals(-1, PeriodFormatter.parsePeriod(null));
		assertEquals(-1, PeriodFormatter.parsePeriod(""));
		assertEquals(-1, PeriodFormatter.parsePeriod("   "));
		assertEquals(-1, PeriodFormatter.parsePeriod("abc"));    // used to be 0
		assertEquals(-1, PeriodFormatter.parsePeriod("5x"));     // unknown unit, used to be 5
		assertEquals(-1, PeriodFormatter.parsePeriod("5H"));     // units are case-sensitive
		assertEquals(-1, PeriodFormatter.parsePeriod("-5s"));    // sign, used to be 5000
		assertEquals(-1, PeriodFormatter.parsePeriod("+5s"));
		assertEquals(-1, PeriodFormatter.parsePeriod("5 h"));    // unit without number
		assertEquals(-1, PeriodFormatter.parsePeriod("h"));
		assertEquals(-1, PeriodFormatter.parsePeriod("1.5h"));
		assertEquals(-1, PeriodFormatter.parsePeriod("99999999999999999999"));  // overflows a long
		assertEquals(-1, PeriodFormatter.parsePeriod("9999999999999999y"));
	}

	public void testValidPeriodSpacing() throws Exception {
		assertEquals(61_000, PeriodFormatter.parsePeriod(" 1m 1s "));
		assertEquals(1500, PeriodFormatter.parsePeriod("1s500"));
		assertEquals(1500, PeriodFormatter.parsePeriod("1s 500ms"));
		assertEquals(0, PeriodFormatter.parsePeriod("0"));
		assertEquals(3_600_000L*24*365, PeriodFormatter.parsePeriod("1Y"));
		assertEquals(3_600_000L*24*30, PeriodFormatter.parsePeriod("1M"));
		assertEquals(60_000L, PeriodFormatter.parsePeriod("1m"));     // m is a minute, M a month
	}
}
