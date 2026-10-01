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
package tests.csv;

import org.monflabs.demodata.northwind.pojo.NorthwindTables;
import org.monflabs.demodata.northwind.pojo._Record;
import org.monflabs.demodata.northwind.pojo._Table;
import org.monflabs.json.JsonArray;

import tests.ProjectTestCase;

public class NorthWindImportTest extends ProjectTestCase {
	
	public void testCreate() throws Exception {
		NorthwindTables tables = new NorthwindTables(false);

		assertEquals(14, tables.getTables().size());
		assertEquals(0, tables.getTables().get(NorthwindTables.categories).getRecords().size());
		
	}
	
	public void testImport() throws Exception {
		NorthwindTables tables = new NorthwindTables();
		
		assertEquals(14, tables.getTables().size());
		assertEquals(8, tables.getTables().get(NorthwindTables.categories).getRecords().size());
		assertEquals(0, tables.getTables().get(NorthwindTables.customer_customer_demo).getRecords().size());
		assertEquals(0, tables.getTables().get(NorthwindTables.customer_demographics).getRecords().size());
		assertEquals(91, tables.getTables().get(NorthwindTables.customers).getRecords().size());
		assertEquals(9, tables.getTables().get(NorthwindTables.employees).getRecords().size());
		assertEquals(49, tables.getTables().get(NorthwindTables.employee_territories).getRecords().size());
		assertEquals(2155, tables.getTables().get(NorthwindTables.order_details).getRecords().size());
		assertEquals(830, tables.getTables().get(NorthwindTables.orders).getRecords().size());
		assertEquals(77, tables.getTables().get(NorthwindTables.products).getRecords().size());
		assertEquals(4, tables.getTables().get(NorthwindTables.region).getRecords().size());
		assertEquals(6, tables.getTables().get(NorthwindTables.shippers).getRecords().size());
		assertEquals(29, tables.getTables().get(NorthwindTables.suppliers).getRecords().size());
		assertEquals(53, tables.getTables().get(NorthwindTables.territories).getRecords().size());
		assertEquals(51, tables.getTables().get(NorthwindTables.us_states).getRecords().size());
		
		for(_Table<? extends _Record> tb: tables.getTables().values()) {
			JsonArray all = JsonArray.create();
			for(_Record r: tb.getRecords()) {
				all.add(r.toJavaArray());
			}
			support.assertJsonTemplate(all, "export-"+tb.getName()+".json");
		}
	}

	public void testNullsAndLineBreaks() throws Exception {
		NorthwindTables tables = new NorthwindTables();
		_Table<org.monflabs.demodata.northwind.pojo.Employees.Record> employees = tables.getTable(NorthwindTables.employees);
		org.monflabs.demodata.northwind.pojo.Employees.Record davolio = employees.getRecords().get(0);
		// an empty CSV field is a SQL NULL, not an empty string
		assertNull(davolio.photo);
		// the address line break is exported escaped: read as a real one
		assertEquals("507 - 20th Ave. E.\nApt. 2A", davolio.address);
		org.monflabs.demodata.northwind.pojo.Employees.Record fuller = employees.getRecords().get(1);
		assertNull("the top manager reports to no one", fuller.reports_to);
		// no empty string anywhere
		for(_Table<? extends _Record> tb: tables.getTables().values()) {
			for(_Record r: tb.getRecords()) {
				for(Object v: r.toJavaArray()) {
					assertFalse(tb.getName(), "".equals(v));
				}
			}
		}
	}

	public void testHeaderOnlyTablesAreEmpty() throws Exception {
		NorthwindTables tables = new NorthwindTables();
		assertTrue(tables.getTables().get(NorthwindTables.customer_customer_demo).getRecords().isEmpty());
		assertTrue(tables.getTables().get(NorthwindTables.customer_demographics).getRecords().isEmpty());
	}

	public void testOpenResource() throws Exception {
		try(java.io.BufferedReader r = new java.io.BufferedReader(NorthwindTables.openResource("csv/region.csv"))) {
			assertEquals("region_id,region_description", r.readLine());
		}
		try {
			NorthwindTables.openResource("csv/nope.csv");
			fail();
		} catch(java.io.UncheckedIOException e) {
			// expected
		}
	}

	public void testJavaArrayMatchesTheFields() throws Exception {
		// toJavaArray() lists the record fields, in declaration order (Customers used to
		// repeat postal_code, shifting the following columns)
		NorthwindTables tables = new NorthwindTables();
		for(_Table<? extends _Record> tb: tables.getTables().values()) {
			if(tb.getRecords().isEmpty()) {
				continue;
			}
			_Record r = tb.getRecords().get(0);
			java.util.List<Object> expected = new java.util.ArrayList<>();
			for(java.lang.reflect.Field f: r.getClass().getDeclaredFields()) {
				if(java.lang.reflect.Modifier.isPublic(f.getModifiers()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					expected.add(f.get(r));
				}
			}
			JsonArray actual = r.toJavaArray();
			assertEquals(tb.getName(), expected.size(), actual.size());
			for(int i=0; i<expected.size(); i++) {
				assertEquals(tb.getName()+"["+i+"]", expected.get(i), actual.get(i));
			}
		}
	}
}
