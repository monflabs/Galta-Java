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
package org.monflabs.demodata.northwind.pojo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.csv.CsvSource;
import org.monflabs.json.impexp.pojo.PojoTarget;

/**
 * The Northwind tables, loaded from the CSV files bundled in
 * {@code northwind/csv/} (one file per table, header row first).
 * <p>
 * How the values are read:
 * <ul>
 * <li>an empty field is a SQL {@code NULL} and is read as {@code null} (a CSV
 * export cannot tell an empty string from a NULL, and Northwind has no empty
 * strings);</li>
 * <li>the line breaks exported as the two characters backslash and {@code n}
 * (employee addresses) are read as real line breaks;</li>
 * <li>the identifiers keep the types of the record classes: most are
 * {@code String}, including the numeric ones like {@code order_id}; the
 * shipper ({@code Shippers.shipper_id}, {@code Orders.ship_via}) is an
 * {@code int}. The record classes are shared with other projects, so these
 * types are kept as they are;</li>
 * <li>the dates stay ISO-8601 strings ({@code yyyy-MM-dd});</li>
 * <li>a table whose file only has its header row
 * ({@code customer_customer_demo}, {@code customer_demographics}) is empty.</li>
 * </ul>
 */
public class NorthwindTables extends _Tables {

	public static final String categories = "categories";
	public static final String customer_customer_demo = "customer_customer_demo";
	public static final String customer_demographics = "customer_demographics";
	public static final String customers = "customers";
	public static final String employees = "employees";
	public static final String employee_territories = "employee_territories";
	public static final String order_details = "order_details";
	public static final String orders = "orders";
	public static final String products = "products";
	public static final String region = "region";
	public static final String shippers = "shippers";
	public static final String suppliers = "suppliers";
	public static final String territories = "territories";
	public static final String us_states = "us_states";

	public NorthwindTables() {
		this(true);
	}
	public NorthwindTables(boolean importData) {
		reset(importData);
	}
	
	public void reset(boolean importData) {
		getTables().clear();
		reset(categories, new Categories(),importData);
		reset(customer_customer_demo, new CustomerCustomerDemo(),importData);
		reset(customer_demographics, new CustomerDemographics(),importData);
		reset(customers, new Customers(),importData);
		reset(employees, new Employees(),importData);
		reset(employee_territories, new EmployeeTerritories(),importData);
		reset(order_details, new OrderDetails(),importData);
		reset(orders, new Orders(),importData);
		reset(products, new Products(),importData);
		reset(region, new Region(),importData);
		reset(shippers, new Shippers(),importData);
		reset(suppliers, new Suppliers(),importData);
		reset(territories, new Territories(),importData);
		reset(us_states, new UsStates(),importData);
	}

	private void reset(String tableName, _Table<? extends _Record> table, boolean importData) {
		getTables().put(tableName, table);
		
		if(importData) {
			CsvSource source = CsvSource.newBuilder()
					.firstRowAsHeader(true)
					.reader(() -> openResource("csv/"+tableName+".csv"))
					.build();
			
			
			@SuppressWarnings({ "unchecked", "rawtypes" })
			PojoTarget<Object> target = PojoTarget
				.newBuilder()
				.writer( (content) -> {
					JsonObject o = (JsonObject)content.getJson();
					Object r = table.createRecord();
					for(Map.Entry<String, Object> e: o.entrySet()) {
						getAccessor().putMember(r, e.getKey(), readValue(e.getValue()));
					}
					((List)table.getRecords()).add(r);
				})
				.build();

			source.exportTo(target);
		}
	}

	/**
	 * A CSV value as stored in a record: an empty field is a NULL, an escaped
	 * line break is a real one.
	 */
	static Object readValue(Object value) {
		if(value instanceof String s) {
			if(s.isEmpty()) {
				return null;
			}
			return s.indexOf('\\')>=0 ? s.replace("\\n", "\n") : s;
		}
		return value;
	}

	/**
	 * Opens one of the bundled files, read as UTF-8 through this module's own
	 * class loader.
	 *
	 * @param path a path relative to the {@code northwind/} folder, e.g.
	 * {@code csv/orders.csv} or {@code postgresql/create-schema.sql}
	 * @throws UncheckedIOException when there is no such file
	 */
	public static Reader openResource(String path) {
		String resource = "northwind/"+path;
		InputStream is = NorthwindTables.class.getClassLoader().getResourceAsStream(resource);
		if(is==null) {
			throw new UncheckedIOException(new IOException("Missing resource "+resource));
		}
		return new InputStreamReader(is, StandardCharsets.UTF_8);
	}
}
