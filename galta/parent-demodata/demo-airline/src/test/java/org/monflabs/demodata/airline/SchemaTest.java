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
package org.monflabs.demodata.airline;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.tests.__BaseTestCase;

/**
 * The airline schema lists the collections of the demo database (see the module README).
 */
public class SchemaTest extends __BaseTestCase {

	public void testCollections() {
		List<String> names = new ArrayList<>();
		for(Schema.COLLECTIONS c: Schema.COLLECTIONS.values()) {
			names.add(c.name());
		}
		assertEquals(List.of("Bookings", "Tickets", "Tickets_flights", "Boarding_passes", "Airports", "Flights", "Aircrafts", "Seats"), names);
	}
}
