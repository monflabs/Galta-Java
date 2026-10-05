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
package tests.json.jsonvalues;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.jsonpath.JsonValues;

import tests.ProjectTestCase;

public class StreamLikeTest extends ProjectTestCase {
	
	public void testPropertyGet() {
		JsonArray a = JsonArray.parse("[{a:1, b:2}, {a:3}]");
		
		JsonValues v = JsonValues.flat(a);
		assertEquals(2, v._size());

		JsonValues v2 = v.get("a");
		assertEquals(2, v2._size());
		assertEquals(1, v2._get(0));
		assertEquals(3, v2._get(1));

		JsonValues v3 = v.get("b");
		assertEquals(1, v3._size());
		assertEquals(2, v3._get(0));

		JsonValues v4 = v.get("c");
		assertEquals(0, v4._size());
	}

	public void testIndexGet() {
		JsonArray a = JsonArray.parse("[[1,2,3],[4,5],[6]]");
		
		JsonValues v = JsonValues.flat(a);
		assertEquals(3, v._size());

		JsonValues v2 = v.get(0);
		assertEquals(3, v2._size());
		assertEquals(1, v2._get(0));
		assertEquals(4, v2._get(1));
		assertEquals(6, v2._get(2));

		JsonValues v3 = v.get(1);
		assertEquals(2, v3._size());
		assertEquals(2, v3._get(0));
		assertEquals(5, v3._get(1));

		JsonValues v4 = v.get(2);
		assertEquals(1, v4._size());
		assertEquals(3, v4._get(0));

		JsonValues v5 = v.get(3);
		assertEquals(0, v5._size());
	}

	public void testFlat() {
		JsonArray a = JsonArray.parse("[1,2,3]");
		
		JsonValues v = JsonValues.of(a);
		assertEquals(1, v._size());
		assertEquals(a, v.value());
		
		JsonValues v2 = v.flat();
		assertEquals(1, v._size());
		assertEquals(3, v2._size());
		assertEquals(1, v2._get(0));
		assertEquals(2, v2._get(1));
		assertEquals(3, v2._get(2));

		JsonValues w = JsonValues.parseAndFlat("[[1],2,3,[4,5]]");
		assertEquals(4, w._size());
		w = w.flat();
		assertEquals(5, w._size());
		assertEquals(JsonArray.of(1,2,3,4,5), w.toJsonArray());

		JsonValues x = JsonValues.parseAndFlat("[{x:1},2,3,{a:4,b:5}]");
		assertEquals(4, x._size());
		x = x.flat();
		assertEquals(5, x._size());
		assertEquals(JsonArray.of(1,2,3,4,5), x.toJsonArray());
	}

	public void testTakeWhile() {
		JsonArray a = JsonArray.parse("[1,2,3]");
		
		JsonValues v = JsonValues.flat(a).takeWhile( (value) -> value.intValue()<2 );
		assertEquals(1, v._size());
		assertEquals(1, v._get(0));
	}
	
	public void testDropWhile() {
		JsonArray a = JsonArray.parse("[1,2,3]");
		
		JsonValues v = JsonValues.flat(a).dropWhile( (value) -> value.intValue()<2 );
		assertEquals(2, v._size());
		assertEquals(2, v._get(0));
		assertEquals(3, v._get(1));
	}
	
	public void testMap() {
		JsonArray a = JsonArray.parse("[1,2,3]");
		
		JsonValues v = JsonValues.of(a);
		assertEquals(1, v._size());
		assertEquals(a, v.value());
		
		JsonValues v2 = v.flat();
		assertEquals(1, v._size());
		assertEquals(3, v2._size());
		assertEquals(1, v2._get(0));
		assertEquals(2, v2._get(1));
		assertEquals(3, v2._get(2));
	}

	public void testIterator() {
		{
			JsonValues v = JsonValues.EMPTY;
			AtomicInteger i = new AtomicInteger();
			for(JsonValues o: v) {
				i.addAndGet(o.intValue());
			}
			assertEquals(0, i.intValue());
		}
		{
			JsonValues v = JsonValues.of(1);
			AtomicInteger i = new AtomicInteger();
			for(JsonValues o: v) {
				i.addAndGet(o.intValue());
			}
			assertEquals(1, i.intValue());
		}
		{
			JsonArray a = JsonArray.parse("[1,2,3]");
			JsonValues v = JsonValues.flat(a);
			AtomicInteger i = new AtomicInteger();
			for(JsonValues o: v) {
				i.addAndGet(o.intValue());
			}
			assertEquals(6, i.intValue());
		}
	}

	public void testRawIterator() {
		{
			JsonValues v = JsonValues.EMPTY;
			AtomicInteger i = new AtomicInteger();
			for(Iterator<Object> it=v.rawIterator(); it.hasNext(); ) {
				Object o = it.next();
				i.addAndGet(JsonUtil.checkInt(o));
			}
			assertEquals(0, i.intValue());
		}
		{
			JsonValues v = JsonValues.of(1);
			AtomicInteger i = new AtomicInteger();
			for(Iterator<Object> it=v.rawIterator(); it.hasNext(); ) {
				Object o = it.next();
				i.addAndGet(JsonUtil.checkInt(o));
			}
			assertEquals(1, i.intValue());
		}
		{
			JsonArray a = JsonArray.parse("[1,2,3]");
			JsonValues v = JsonValues.flat(a);
			AtomicInteger i = new AtomicInteger();
			for(Iterator<Object> it=v.rawIterator(); it.hasNext(); ) {
				Object o = it.next();
				i.addAndGet(JsonUtil.checkInt(o));
			}
			assertEquals(6, i.intValue());
		}
	}

	public void testForEach() {
		{
			JsonValues v = JsonValues.EMPTY;
			AtomicInteger i = new AtomicInteger();
			v.forEach( (e) -> {
				i.addAndGet(e.intValue());
			});
			assertEquals(0, i.intValue());
		}
		{
			JsonValues v = JsonValues.of(1);
			AtomicInteger i = new AtomicInteger();
			v.forEach( (e) -> {
				i.addAndGet(e.intValue());
			});
			assertEquals(1, i.intValue());
		}
		{
			JsonArray a = JsonArray.parse("[1,2,3]");
			JsonValues v = JsonValues.flat(a);
			AtomicInteger i = new AtomicInteger();
			v.forEach( (e) -> {
				i.addAndGet(e.intValue());
			});
			assertEquals(6, i.intValue());
		}
	}

	public void testRawForEach() {
		{
			JsonValues v = JsonValues.EMPTY;
			AtomicInteger i = new AtomicInteger();
			v.rawForEach( (e) -> {
				i.addAndGet(JsonUtil.checkInt(e));
			});
			assertEquals(0, i.intValue());
		}
		{
			JsonValues v = JsonValues.of(1);
			AtomicInteger i = new AtomicInteger();
			v.rawForEach( (e) -> {
				i.addAndGet(JsonUtil.checkInt(e));
			});
			assertEquals(1, i.intValue());
		}
		{
			JsonArray a = JsonArray.parse("[1,2,3]");
			JsonValues v = JsonValues.flat(a);
			AtomicInteger i = new AtomicInteger();
			v.rawForEach( (e) -> {
				i.addAndGet(JsonUtil.checkInt(e));
			});
			assertEquals(6, i.intValue());
		}
	}

	public void testPeek() {
		{
			JsonValues v = JsonValues.EMPTY;
			AtomicInteger i = new AtomicInteger();
			JsonValues r = v.peek( (e) -> {
				i.addAndGet(e.intValue());
			});
			assertSame(v, r);
			assertEquals(0, i.intValue());
		}
		{
			JsonValues v = JsonValues.of(1);
			AtomicInteger i = new AtomicInteger();
			JsonValues r = v.peek( (e) -> {
				i.addAndGet(e.intValue());
			});
			assertSame(v, r);
			assertEquals(1, i.intValue());
		}
		{
			JsonArray a = JsonArray.parse("[1,2,3]");
			JsonValues v = JsonValues.flat(a);
			AtomicInteger i = new AtomicInteger();
			JsonValues r = v.peek( (e) -> {
				i.addAndGet(e.intValue());
			});
			assertSame(v, r);
			assertEquals(6, i.intValue());
		}
	}

	public void testFilter() {
		JsonValues v = JsonValues.parseAndFlat("[1,2,3]");
		assertEquals(3, v._size());
		
		JsonValues v2 = v.filter( (val) -> val.intValue()>=2 );
		assertEquals(2, v2._size());
		assertEquals(2, v2._get(0));
		assertEquals(3, v2._get(1));
		
		JsonValues v3 = v.filter( (val) -> val.intValue()>=3 );
		assertEquals(1, v3._size());
		assertEquals(3, v3._get(0));

		JsonValues v4 = JsonValues.parseAndFlat("[]").filter( (val) -> val.intValue()>=3 );
		assertEquals(0, v4._size());
	}

	public void testRemove() {
		JsonValues v = JsonValues.parseAndFlat("[1,2,3]");
		assertEquals(3, v._size());
		
		JsonValues v2 = v.reject( (val) -> val.intValue()<2 );
		assertEquals(2, v2._size());
		assertEquals(2, v2._get(0));
		assertEquals(3, v2._get(1));
		
		JsonValues v3 = v.reject( (val) -> val.intValue()<3 );
		assertEquals(1, v3._size());
		assertEquals(3, v3._get(0));

		JsonValues v4 = JsonValues.parseAndFlat("[]").reject( (val) -> val.intValue()<0 );
		assertEquals(0, v4._size());
	}

	public void testDistinct() {
		JsonValues v = JsonValues.parseAndFlat("[1,2,3,2,3,2,1]");
		assertEquals(7, v._size());
		
		JsonValues v2 = v.distinct();
		assertEquals(3, v2._size());
		assertEquals(1, v2._get(0));
		assertEquals(2, v2._get(1));
		assertEquals(3, v2._get(2));
		
		JsonValues w = JsonValues.parseAndFlat("[1]");
		assertEquals(1, w._size());
		JsonValues w2 = w.distinct();
		assertEquals(1, w2._size());
		assertEquals(1, w2._get(0));
		
		JsonValues x = JsonValues.parseAndFlat("[]");
		assertEquals(0, x._size());
		JsonValues x2 = x.distinct();
		assertEquals(0, x2._size());
	}

	public void testSorted() {
		JsonValues v = JsonValues.parseAndFlat("[3,2,4,5,1,6]");
		
		assertEquals(JsonValues.parseAndFlat("[1,2,3,4,5,6]"), v.sorted());
		assertEquals(JsonValues.parseAndFlat("[6,5,4,3,2,1]"), v.sorted(false));
		assertEquals(JsonValues.parseAndFlat("[2,4,6,1,3,5]"), v.sorted( (v1,v2) -> {
			int i1 = v1.intValue();
			int i2 = v2.intValue();
			int d = i1%2 - i2%2; 
			if(d!=0) {
				return d;
			}
			return i1-i2;
		}));
	}
	
	public void testMinMax() {
		JsonValues v = JsonValues.parseAndFlat("[1,2,3]");
		assertEquals(1, v.min().value());
		assertEquals(1, v.min().value());
		assertEquals(1, v.min( (o1,o2) -> {
			return o1.compareTo(o2);
		}).value());
		assertEquals(3, v.max().value());
		assertEquals(3, v.max( (o1,o2) -> {
			return o1.compareTo(o2);
		}).value());
		assertEquals(1, v.max( (o1,o2) -> {
			return -o1.compareTo(o2);
		}).value());

		JsonValues v2 = JsonValues.parseAndFlat("[]");
		assertEquals(JsonValues.EMPTY, v2.min());
		assertEquals(JsonValues.EMPTY, v2.min( (o1,o2) -> {
			return o1.compareTo(o2);
		}));
		assertEquals(JsonValues.EMPTY, v2.max());
		assertEquals(JsonValues.EMPTY, v2.max( (o1,o2) -> {
			return -o1.compareTo(o2);
		}));

		JsonValues v3 = JsonValues.parseAndFlat("[4]");
		assertEquals(4, v3.min().value());
		assertEquals(4, v3.min( (o1,o2) -> {
			return o1.compareTo(o2);
		}).value());
		assertEquals(4, v3.max().value());
		assertEquals(4, v3.max( (o1,o2) -> {
			return o1.compareTo(o2);
		}).value());
	}
}
