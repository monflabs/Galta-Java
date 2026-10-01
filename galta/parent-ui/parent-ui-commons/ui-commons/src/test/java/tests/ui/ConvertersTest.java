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
package tests.ui;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.ui.converters.BooleanConverter;
import org.monflabs.ui.converters.LongConverter;
import org.monflabs.ui.converters.TextConverter;
import org.monflabs.ui.lookup.ILookupChangeListener;
import org.monflabs.ui.lookup.StringArrayLookup;

import tests.ProjectTestCase;

public class ConvertersTest extends ProjectTestCase {

	public void testBooleanStringConverter() {
		BooleanConverter.CString c = BooleanConverter.stringConverter;
		assertTrue(c.valueToBoolean("true"));
		assertFalse(c.valueToBoolean("false"));
		assertFalse(c.valueToBoolean("other"));
		assertEquals("true", c.booleanToValue(true));
		assertEquals("false", c.booleanToValue(false));
		BooleanConverter.CString yn = new BooleanConverter.CString("Y", "N", true);
		assertFalse(yn.valueToBoolean("N"));
		assertTrue(yn.valueToBoolean("Y"));
		assertTrue(yn.valueToBoolean("?")); // default
	}

	public void testLongStringConverter() {
		LongConverter.CString c = new LongConverter.CString(7);
		assertEquals(12L, c.valueToLong("12"));
		assertEquals(12L, c.valueToLong(" 12 "));
		assertEquals(7L, c.valueToLong("abc"));
		assertEquals(7L, c.valueToLong(null));
		assertEquals("42", c.longToValue(42));
	}

	public void testTextConvertersNull() {
		assertNull(TextConverter.stringConverter.valueToString(null));
		assertNull(TextConverter.stringConverter.stringToValue(null));
		assertNull(TextConverter.booleanConverter.valueToString(null));
		assertNull(TextConverter.booleanConverter.stringToValue(null));
		assertNull(TextConverter.booleanConverter.stringToValue(""));
		assertNull(TextConverter.intConverter.valueToString(null));
		assertNull(TextConverter.intConverter.stringToValue(null));
		assertNull(TextConverter.intConverter.stringToValue(""));
		assertNull(TextConverter.longConverter.valueToString(null));
		assertNull(TextConverter.longConverter.stringToValue(null));
		assertNull(TextConverter.doubleConverter.valueToString(null));
		assertNull(TextConverter.doubleConverter.stringToValue(null));
		// values still convert both ways
		assertEquals("12", TextConverter.intConverter.valueToString(12));
		assertEquals(Integer.valueOf(12), TextConverter.intConverter.stringToValue("12"));
		assertEquals(Long.valueOf(12), TextConverter.longConverter.stringToValue("12"));
		assertEquals(Double.valueOf(1.5), TextConverter.doubleConverter.stringToValue("1.5"));
		assertEquals("true", TextConverter.booleanConverter.valueToString(true));
		assertEquals(Boolean.TRUE, TextConverter.booleanConverter.stringToValue("true"));
	}

	public void testListenerRemovingItself() {
		StringArrayLookup l = new StringArrayLookup("a", "b");
		List<String> calls = new ArrayList<>();
		ILookupChangeListener<String> self = new ILookupChangeListener<String>() {
			@Override
			public void lookupChanged(org.monflabs.ui.lookup.ILookup<String> lookup) {
				calls.add("self");
				l.removeLookupChangeListener(this);
			}
		};
		l.addLookupChangeListener(self);
		l.addLookupChangeListener(lookup -> calls.add("other"));
		l.notifyLookupChanged();
		assertEquals(List.of("self", "other"), calls);
		l.notifyLookupChanged();
		assertEquals(List.of("self", "other", "other"), calls);
	}
}
