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
package tests.util;

import static org.junit.Assert.assertThrows;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.monflabs.util.http.HttpUtils;

import tests.ProjectTestCase;

public class HttpUtilsTest extends ProjectTestCase {

	private static Map<String,String> map(String...kv) {
		Map<String,String> m = new LinkedHashMap<>();
		for(int i=0; i<kv.length; i+=2) {
			m.put(kv[i], kv[i+1]);
		}
		return m;
	}

	public void testEncodeFormData() throws Exception {
		assertEquals("a=b+c&x=%26%3D%C3%A9", HttpUtils.encodeFormData(map("a","b c","x","&=é")));
		assertEquals("", HttpUtils.encodeFormData(null));
		assertEquals("", HttpUtils.encodeFormData(Collections.emptyMap()));
		// An empty value keeps its '=' (it used to be dropped), a null value is a bare key
		assertEquals("a=&b", HttpUtils.encodeFormData(map("a","","b",null)));
		// A null key used to throw a NullPointerException
		assertThrows(IllegalArgumentException.class, () -> HttpUtils.encodeFormData(map(null,"v")));
	}

	public void testAppendQueryString() throws Exception {
		assertEquals("http://h/p?a=b", HttpUtils.appendQueryString("http://h/p", map("a","b")));
		assertEquals("http://h/p?x=1&a=b", HttpUtils.appendQueryString("http://h/p?x=1", map("a","b")));
		// The query goes before the fragment (it used to be appended inside it)
		assertEquals("http://h/p?a=b+c#frag", HttpUtils.appendQueryString("http://h/p#frag", map("a","b c")));
		assertEquals("http://h/p?x=1&a=b#f", HttpUtils.appendQueryString("http://h/p?x=1#f", map("a","b")));
		// No separator after a trailing '?' or '&' (it used to give "?&a")
		assertEquals("http://h/p?a=b", HttpUtils.appendQueryString("http://h/p?", map("a","b")));
		assertEquals("http://h/p?x=1&a=b", HttpUtils.appendQueryString("http://h/p?x=1&", map("a","b")));
		assertEquals("http://h/p#f", HttpUtils.appendQueryString("http://h/p#f", map()));
	}
}
