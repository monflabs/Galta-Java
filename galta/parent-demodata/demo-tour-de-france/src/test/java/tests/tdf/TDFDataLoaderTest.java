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
package tests.tdf;

import java.util.List;

import org.monflabs.demodata.tourdefrance.TDFDataLoader;
import org.monflabs.demodata.tourdefrance.Tour;
import org.monflabs.util.ResourceLoader;

import junit.framework.TestCase;

public class TDFDataLoaderTest extends TestCase {

	public void testLoadTours() {
		List<Tour> tours = TDFDataLoader.loadTours();
		assertEquals(109, tours.size());
		Tour first = tours.get(0);
		assertEquals(1903, first.getYear());
		assertEquals(60, first.getStarters());
		assertEquals(21, first.getFinishers());
		assertTrue(first.isArchived());
		// The CSV files are UTF-8: the distance keeps its non-breaking spaces
		assertEquals("2,428 km (1,509 mi)", first.getDistance());
	}

	public void testResourcesAreUtf8() {
		for(String name: new String[] {"tdf_tours.csv", "tdf_winners.csv", "tdf_stages.csv", "tdf_finishers.csv", "data_dictionary.csv"}) {
			String text = ResourceLoader.loadTextResource("tourdefrance/"+name);
			assertFalse(name+" is not UTF-8", text.indexOf('�')>=0);
		}
		String winners = ResourceLoader.loadTextResource("tourdefrance/tdf_winners.csv");
		assertTrue(winners.contains("La Française"));
		String tours = ResourceLoader.loadTextResource("tourdefrance/tdf_tours.csv");
		assertTrue(tours.contains("1–19 July 1903"));
	}
}
