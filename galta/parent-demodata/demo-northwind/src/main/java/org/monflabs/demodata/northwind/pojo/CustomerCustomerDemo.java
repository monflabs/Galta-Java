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

import org.monflabs.json.JsonArray;

public class CustomerCustomerDemo extends _Table<CustomerCustomerDemo.Record> {

	public static class Record extends _Record {
		public String customer_id;
		public String customer_type_id;
		
		@Override
		public JsonArray toJavaArray() {
			return JsonArray.of(customer_id,customer_type_id);
		}
	}
	
	public CustomerCustomerDemo() {
		super(NorthwindTables.customer_customer_demo);
	}
	
	@Override
	public Record createRecord() {
		return new Record();
	}
}
