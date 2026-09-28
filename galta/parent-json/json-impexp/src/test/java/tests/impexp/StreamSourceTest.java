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
package tests.impexp;

import java.util.Arrays;
import java.util.List;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.container.StreamSource;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

import tests.ProjectTestCase;
import tests.util.StaticTarget;

public class StreamSourceTest extends ProjectTestCase {

	private static List<Object> list = Arrays.asList(
		JsonObject.parse("{a: 1, b:'A'}"),
		JsonObject.parse("{a: 2, b:'B'}"),
		JsonObject.parse("{a: 3, b:'C'}")
	);

	public void testSource() throws Exception {
		StreamSource source = StreamSource.newBuilder()
				.streamFactory(list::stream)
				.build();
		checkStreamSource(source, -1, "stream.json");
	}
	public void testSourceCount() throws Exception {
		StreamSource source = StreamSource.newBuilder()
				.streamFactory(list::stream)
				.estimatedCount(list.size())
				.build();
		checkStreamSource(source, 3, "stream.json");
	}
	public void testSourceCol() throws Exception {
		StreamSource source = StreamSource.newBuilder()
				.streamFactory(list::stream)
				.collectionFunction( (o) -> "COL_"+((JsonObject)o).getString("b") )
				.build();
		checkStreamSource(source, -1, "stream-col.json");
	}
	public void testSourceKey() throws Exception {
		StreamSource source = StreamSource.newBuilder()
				.streamFactory(list::stream)
				.keyFunction( (o) -> "K_"+((JsonObject)o).getString("b") )
				.build();
		checkStreamSource(source, -1, "stream-key.json");
	}
	public void testSourceVal() throws Exception {
		StreamSource source = StreamSource.newBuilder()
				.streamFactory(list::stream)
				.valueFunction( (o) -> ((JsonObject)o).deepClone().put("c","CC") )
				.build();
		checkStreamSource(source, -1, "stream-val.json");
	}

	private void checkStreamSource(StreamSource source, long count, String template) throws Exception {
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		long cnt = source.estimatedCount();
		assertEquals(count, cnt);
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), template);
	}
}
