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

import org.monflabs.json.impexp.container.JsonContainerTarget;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

import tests.ProjectTestCase;
import tests.util.StaticSource;

public class MemoryTargetTest extends ProjectTestCase {

	public void testTarget() throws Exception {
		checkTarget( JsonInMemoryFormat.RECORDS, 
				  "tgt-records");
		checkTarget( JsonInMemoryFormat.RECORDSWITHKEYS, 
				  "tgt-recordswithkeys");
		checkTarget( JsonInMemoryFormat.RECORDSBYCOL, 
				  "tgt-recordsbycol");
		checkTarget( JsonInMemoryFormat.RECORDSBYKEY, 
				  "tgt-recordsbykey");
		checkTarget( JsonInMemoryFormat.RECORDSBYCOLKEY, 
				  "tgt-recordsbycolbykey");
	}

	private void checkTarget(JsonInMemoryFormat format, String template) throws Exception {
		StaticSource source = StaticSource.newBuilder().build();

		JsonContainerTarget target = JsonContainerTarget.newBuilder()
								.format(format)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();

		source.exportTo(target);
		support.assertJsonTemplate(target.getContainer(), template);
	}
}
