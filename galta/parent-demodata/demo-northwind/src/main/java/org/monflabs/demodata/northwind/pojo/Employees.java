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

public class Employees extends _Table<Employees.Record> {

	public static class Record extends _Record {
		public String employee_id;
		public String last_name;
		public String first_name;
		public String title;
		public String title_of_courtesy;
		public String birth_date;
		public String hire_date;
		public String address;
		public String city;
		public String region;
		public String postal_code;
		public String country;
		public String home_phone;
		public String extension;
		public String photo;
		public String notes;
		public String reports_to;
		public String photo_path;
		
		@Override
		public JsonArray toJavaArray() {
			return JsonArray.of(employee_id,last_name,first_name,title,title_of_courtesy,birth_date,hire_date,address,city,region,postal_code,country,home_phone,extension,photo,notes,reports_to,photo_path);
		}
	}
	
	public Employees() {
		super(NorthwindTables.employees);
	}
	
	@Override
	public Record createRecord() {
		return new Record();
	}
}
