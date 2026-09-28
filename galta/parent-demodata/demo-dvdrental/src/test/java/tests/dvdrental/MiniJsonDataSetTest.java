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
package tests.dvdrental;

import org.monflabs.demodata.dvdrental.MiniJsonDataSet;
import org.monflabs.demodata.dvdrental.Schema;
import org.monflabs.json.JsonFactory;

import junit.framework.TestCase;

public class MiniJsonDataSetTest extends TestCase {

	public void testLoad() {
		MiniJsonDataSet ds = new MiniJsonDataSet(JsonFactory.get());
		assertFalse(ds.getActors().isEmpty());
		assertFalse(ds.getCategories().isEmpty());
		assertFalse(ds.getFilms().isEmpty());
		assertFalse(ds.getFilms_actors().isEmpty());
		assertFalse(ds.getFilms_actors_direct().isEmpty());
		assertFalse(ds.getFilms_categories().isEmpty());
		assertFalse(ds.getLanguages().isEmpty());
	}

	public void testSchemaMatchesTheResources() {
		// Every collection of the schema is a resource of the dataset
		for(Schema.COLLECTIONS c: Schema.COLLECTIONS.values()) {
			assertNotNull(c.name(), MiniJsonDataSet.class.getClassLoader().getResource("mini-dataset/dvdrental/"+c.getResourceName()));
		}
		assertEquals(7, Schema.COLLECTIONS.values().length);
	}
}
