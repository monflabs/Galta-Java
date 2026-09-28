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

public class Suppliers extends _Table<Suppliers.Record> {

	public static class Record extends _Record {
		public String supplier_id;
		public String company_name;
		public String contact_name;
		public String contact_title;
		public String address;
		public String city;
		public String region;
		public String postal_code;
		public String country;
		public String phone;
		public String fax;
		public String homepage;
		
		@Override
		public JsonArray toJavaArray() {
			return JsonArray.of(supplier_id,company_name,contact_name,contact_title,address,city,region,postal_code,country,phone,fax,homepage);
		}
	}
	
	public Suppliers() {
		super(NorthwindTables.suppliers);
	}
	
	@Override
	public Record createRecord() {
		return new Record();
	}
}
