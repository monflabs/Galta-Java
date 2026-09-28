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

import java.util.Map;

import org.monflabs.util.BaseException;
import org.monflabs.util.ConfigurationProperties;
import org.monflabs.util.PathUtil;
import org.monflabs.util.TextBuilder;
import org.monflabs.util.http.HttpUtils;
import org.monflabs.util.io.FastStringReader;
import org.monflabs.util.performance.PerformanceWatchCollection;

import tests.ProjectTestCase;

/**
 * Regression tests for small utilities.
 */
public class MiscUtilTest extends ProjectTestCase {

	public void testTextBuilderSubSequence() {
		TextBuilder b = new TextBuilder("hello world");
		assertEquals( "world", b.subSequence(6, 11).toString() );
		assertEquals( 11, b.length() );
	}

	public void testAppendQueryString() {
		assertEquals( "http://x/a?k=v", HttpUtils.appendQueryString("http://x/a", Map.of("k","v")) );
		assertEquals( "http://x/a?p=1&k=v", HttpUtils.appendQueryString("http://x/a?p=1", Map.of("k","v")) );
		assertEquals( "http://x/a", HttpUtils.appendQueryString("http://x/a", Map.of()) );
	}

	public void testConfigurationProperties() {
		// The env var name comes from the key; a missing variable yields the default
		assertEquals( "dflt", ConfigurationProperties.get().get("galta.test.no.such.key", "dflt") );
		assertNull( ConfigurationProperties.get().get("galta.test.no.such.key") );
		String path = System.getenv("PATH");
		if(path!=null) {
			assertEquals( path, ConfigurationProperties.get().get("path", "dflt") );
		}
	}

	public void testFastStringReaderSkip() throws Exception {
		FastStringReader r = new FastStringReader("abcdef");
		assertEquals( 2, r.skip(2) );
		assertEquals( 'c', r.read() );
		assertEquals( 0, r.skip(-5) );
		assertEquals( 'd', r.read() );
		assertEquals( 2, r.skip(Long.MAX_VALUE) );
		assertEquals( -1, r.read() );
	}

	public void testBaseExceptionCause() {
		RuntimeException root = new RuntimeException("root");
		RuntimeException wrapped = new RuntimeException("wrapped", root);
		assertSame( root, BaseException.getCause(wrapped) );
		assertEquals( "wrapped\nroot", BaseException.getMessages(wrapped) );
		// A cycle in the cause chain must not loop forever
		Throwable a = new Throwable("a");
		Throwable b = new Throwable("b", a);
		a.initCause(b);
		assertEquals( "a\nb", BaseException.getMessages(a) );
	}

	public void testPerformanceWatchCollectionIterations() throws Exception {
		PerformanceWatchCollection c = new PerformanceWatchCollection("t");
		int[] runs = new int[1];
		c.run("k", () -> runs[0]++, 3, 2);
		assertEquals( 5, runs[0] );
		runs[0] = 0;
		c.runWithException("k2", () -> runs[0]++, 4, 1);
		assertEquals( 5, runs[0] );
	}

	public void testPathUtilCompose() {
		String[] parts = new String[] {"a","b","c"};
		assertEquals( "a/b/c", PathUtil.POSIX.compose(parts) );
		assertEquals( "b/c", PathUtil.POSIX.compose(parts, 1, Integer.MAX_VALUE) );
		assertEquals( "b", PathUtil.POSIX.compose(parts, 1, 1) );
	}

	public void testPathUtilRelativePath() {
		assertEquals( "b/c", PathUtil.POSIX.getRelativePath("/a", "/a/b/c") );
		assertEquals( "", PathUtil.POSIX.getRelativePath("/a", "/a") );
		assertNull( PathUtil.POSIX.getRelativePath("/a", "/ab/c") );
		// A root base has no trailing separator to skip
		assertEquals( "a/b/c", PathUtil.POSIX.getRelativePath("/", "/a/b/c") );
		assertEquals( "b/c", PathUtil.POSIX.getRelativePath("/a/", "/a/b/c") );
		assertNull( PathUtil.POSIX.getRelativePath("/x/", "/a/b/c") );
		assertNull( PathUtil.POSIX.getRelativePath(null, "/a") );
		assertThrows(RuntimeException.class, () -> { throw new RuntimeException(); });
	}
}
