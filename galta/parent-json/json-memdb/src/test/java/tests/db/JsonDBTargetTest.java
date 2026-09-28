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
package tests.db;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.container.JsonContainerSource;
import org.monflabs.json.impexp.container.JsonInMemoryFormat;
import org.monflabs.json.impexp.db.JsonDbTarget;
import org.monflabs.json.impexp.db.MemoryJsonDb;
import org.monflabs.json.impexp.impl.JsonTargetImpl;

import tests.ProjectTestCase;

public class JsonDBTargetTest extends ProjectTestCase {


	public void testTarget() throws Exception {
		JsonContainerSource source = JsonContainerSource.newBuilder().container(JSON).format(JsonInMemoryFormat.RECORDSBYCOLKEY).build();
		
		MemoryJsonDb db = new MemoryJsonDb();

		JsonDbTarget target = JsonDbTarget.newBuilder()
								.db(db)
								.estimatedCount(source::estimatedCount)
								.notification(JsonTargetImpl.consoleLogger)
					   			.build();

		source.exportTo(target);
		support.assertJsonTemplate(db.serialize(), "tgt-records");
	}
	
	private static JsonObject JSON = JsonObject.parse(
"""
{
  "col1":{
    "k11":{
      "a":"v11",
      "b":1
    },
    "k12":{
      "a":"v12",
      "b":1
    },
    "k13":{
      "a":"v13",
      "b":1
    },
    "k14":{
      "a":"v14",
      "b":1
    }
  },
  "col2":{
    "k21":{
      "a":"v21",
      "b":2
    },
    "k22":{
      "a":"v22",
      "b":2
    },
    "k23":{
      "a":"v23",
      "b":2
    },
    "k24":{
      "a":"v24",
      "b":2
    },
    "k25":{
      "a":"v25",
      "b":2
    },
    "k26":{
      "a":"v26",
      "b":2
    }
  },
  "":{
    "k31":{
      "a":"v31",
      "b":3
    },
    "*desneiges":{
      "a":"v32",
      "b":3
    },
    "\u4f60\u597d":{
      "a":"N\u01d0 h\u01ceo",
      "b":3
    }
  }
}	
"""
	); 
}
