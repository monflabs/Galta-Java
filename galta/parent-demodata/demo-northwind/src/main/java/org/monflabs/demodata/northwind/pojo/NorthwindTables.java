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

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.csv.CsvSource;
import org.monflabs.json.impexp.pojo.PojoTarget;
import org.monflabs.util.ResourceLoader;

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
					.reader(() -> new StringReader(ResourceLoader.loadTextResource("northwind/csv/"+tableName+".csv")))
					.build();
			
			
			@SuppressWarnings({ "unchecked", "rawtypes" })
			PojoTarget<Object> target = PojoTarget
				.newBuilder()
				.writer( (content) -> {
					JsonObject o = (JsonObject)content.getJson();
					Object r = table.createRecord();
					for(Map.Entry<String, Object> e: o.entrySet()) {
						getAccessor().putMember(r, e.getKey(), e.getValue());
					}
					((List)table.getRecords()).add(r);
				})
				.build();
					
			source.exportTo(target);
		}
	}
}
