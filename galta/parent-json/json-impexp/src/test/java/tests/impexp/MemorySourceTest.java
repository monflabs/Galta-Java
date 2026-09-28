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

import java.util.function.Function;

import org.monflabs.json.JsonContainer;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

import tests.ProjectTestCase;
import tests.util.StaticTarget;

public class MemorySourceTest extends ProjectTestCase {

	public void testSource() throws Exception {
		// Standard imp/exp
		checkSource( JsonInMemoryFormat.RECORDS, 
				  "['a','b','c']",
				  null,
				  null,
				  "src-records",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSWITHKEYS, 
				  "[{collection:'col1',id:'k1',value:'a'},{collection:'col1',id:'k2',value:'b'},{collection:'col2',id:'k1',value:'c'}]",
				  null,
				  null,
				  "src-recordswithkey",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSBYCOL, 
				  "{ col1: ['a','b','c'], col2: ['d','e'] }", 
				  null,
				  null,
				  "src-recordsbycol",
				  5);
		checkSource( JsonInMemoryFormat.RECORDSBYKEY, 
				  "{ k1: 'a', k2: 'b', k3: 'c'}", 
				  null,
				  null,
				  "src-recordsbykey",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSBYCOLKEY, 
				  "{ col1: { k1: 'a', k2: 'b', k3: 'c'}, col2: {k1:'d', k2:'e'} }", 
				  null,
				  null,
				  "src-recordsbycolbykey",
				  5);

		// Use collection name
		checkSource( JsonInMemoryFormat.RECORDS, 
				  "['a','b','c']",
				  (o) -> "col-"+((String)o).toUpperCase(),
				  null,
				  "src-records-col",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSWITHKEYS, 
				  "[{collection:'col1',id:'k1',value:'a'},{collection:'col1',id:'k2',value:'b'},{collection:'col2',id:'k1',value:'c'}]",
				  (o) -> "col-"+((String)o).toUpperCase(), // no impact
				  null,
				  "src-recordswithkeys-col",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSBYCOL, 
				  "{ col1: ['a','b','c'], col2: ['d','e'] }", 
				  (o) -> "col-"+((String)o).toUpperCase(), // no impact
				  null,
				  "src-recordsbycol-col",
				  5);
		checkSource( JsonInMemoryFormat.RECORDSBYKEY, 
				  "{ k1: 'a', k2: 'b', k3: 'c'}", 
				  (o) -> "col-"+((String)o).toUpperCase(), 
				  null,
				  "src-recordsbykey-col",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSBYCOLKEY, 
				  "{ col1: { k1: 'a', k2: 'b', k3: 'c'}, col2: {k1:'d', k2:'e'} }", 
				  (o) -> "col-"+((String)o).toUpperCase(), // no impact
				  null,
				  "src-recordsbycolbykey-col",
				  5);

		// Use key name
		checkSource( JsonInMemoryFormat.RECORDS, 
				  "['a','b','c']",
				  null,
				  (o) -> "key-"+((String)o).toUpperCase(),
				  "src-records-key",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSWITHKEYS, 
				  "[{collection:'col1',id:'k1',value:'a'},{collection:'col1',id:'k2',value:'b'},{collection:'col2',id:'k1',value:'c'}]",
				  null,
				  (o) -> "key-"+((String)o).toUpperCase(), // no impact
				  "src-recordswithkeys-key",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSBYCOL, 
				  "{ col1: ['a','b','c'], col2: ['d','e'] }", 
				  null,
				  (o) -> "key-"+((String)o).toUpperCase(),
				  "src-recordsbycol-key",
				  5);
		checkSource( JsonInMemoryFormat.RECORDSBYKEY, 
				  "{ k1: 'a', k2: 'b', k3: 'c'}", 
				  null,
				  (o) -> "key-"+((String)o).toUpperCase(), // no impact
				  "src-recordsbykey-key",
				  3);
		checkSource( JsonInMemoryFormat.RECORDSBYCOLKEY, 
				  "{ col1: { k1: 'a', k2: 'b', k3: 'c'}, col2: {k1:'d', k2:'e'} }", 
				  null,
				  (o) -> "key-"+((String)o).toUpperCase(), // no impact
				  "src-recordsbycolbykey-key",
				  5);
	}
	private void checkSource(JsonInMemoryFormat format, String json, Function<Object,String> collectionFunction, Function<Object,String> keyFunction, String template, long estimatedCount) throws Exception {
		JsonContainer c = JsonContainer.parse(json);
		
		JsonContainerSource source = JsonContainerSource.newBuilder()
								.format(format)
								.collectionFunction(collectionFunction)
								.keyFunction(keyFunction)
								.container(c)
								.build();
		StaticTarget target = StaticTarget.newBuilder()
				.estimatedCount(source::estimatedCount)
				.notification(JsonTargetImpl.consoleLogger)
				.build();
		
		long cnt = source.estimatedCount();
		assertEquals(estimatedCount, cnt);
		
		source.exportTo(target);
		support.assertJsonTemplate(target.getContentAsJson(), template);
	}
}
