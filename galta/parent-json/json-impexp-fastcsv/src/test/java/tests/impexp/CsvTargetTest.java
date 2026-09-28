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

import java.io.StringWriter;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.csv.CsvTarget;
import org.monflabs.json.impexp.impl.JsonTargetImpl;
import org.monflabs.util.Console;

import tests.ProjectTestCase;

public class CsvTargetTest extends ProjectTestCase {
	
	private static final String JSON = 
	"""
[
  {
    "c1":true,
    "c2":79,
    "c2-1":3,
    "c2-2":4,
    "c2-3":5,
    "c2-4":7,
    "c2-5":67.6,
    "c2-6":78.9,
    "c2-7":456,
    "c2-8":678.89,
    "c3":"mystr",
    "c4":{
      "a":1
    },
    "c5":[
      2,
      3,
      4
    ]
  }
]	
	""";

	// C14: a cell writer is invoked even when the column is absent from the JSON
	public void testComputedColumn() throws Exception {
		JsonContainerSource source = JsonContainerSource.newBuilder()
								.format(JsonInMemoryFormat.RECORDS)
								.container(JsonArray.parse("[{v:1},{v:2}]"))
								.build();
		StringWriter sw = new StringWriter();
		CsvTarget target = CsvTarget.newBuilder()
								.writer(() -> sw)
								.column("computed", (c) -> "X"+((JsonObject)c.getJson()).get("v"))
								.column("v")
								.build();
		source.exportTo(target);
		assertEquals("computed,v\nX1,1\nX2,2\n", sw.toString().replace("\r\n","\n"));
	}

	// C14: inferred columns must not leak from one import into the next
	public void testInferredColumnsReset() throws Exception {
		StringWriter[] sw = new StringWriter[1];
		CsvTarget target = CsvTarget.newBuilder()
								.writer(() -> sw[0])
								.build();
		sw[0] = new StringWriter();
		JsonContainerSource.newBuilder()
								.format(JsonInMemoryFormat.RECORDS)
								.container(JsonArray.parse("[{a:1}]"))
								.build()
								.exportTo(target);
		assertEquals("a\n1\n", sw[0].toString().replace("\r\n","\n"));

		sw[0] = new StringWriter();
		JsonContainerSource.newBuilder()
								.format(JsonInMemoryFormat.RECORDS)
								.container(JsonArray.parse("[{b:2}]"))
								.build()
								.exportTo(target);
		assertEquals("b\n2\n", sw[0].toString().replace("\r\n","\n"));
	}

	public void testFileTarget() throws Exception {
		JsonContainerSource source = JsonContainerSource.newBuilder()
								.format(JsonInMemoryFormat.RECORDS)
								.container(JsonArray.parse(JSON))
								.build();
		
		StringWriter sw = new StringWriter();
		CsvTarget target = CsvTarget.newBuilder()
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
								.writer(() -> sw)
								.column("c1")
								.column("c2")
								.column("c2-1")
								.column("c2-2")
								.column("c2-3")
								.column("c2-4")
								.column("c2-5")
								.column("c2-6")
								.column("c2-7")
								.column("c2-8")
								.column("c3")
								.column("c4")
								.column("c5")
								.build();
		source.exportTo(target);
		Console.log("{0}",sw.toString());
		support.assertTextResult(sw.toString(), "csv-cols.csv");
	}
}
