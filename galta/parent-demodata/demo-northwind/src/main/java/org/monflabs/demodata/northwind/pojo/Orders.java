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

import java.math.BigDecimal;

import org.monflabs.json.JsonArray;

public class Orders extends _Table<Orders.Record> {

	public static class Record extends _Record {
		public String order_id;
		public String customer_id;
		public String employee_id;
		public String order_date;
		public String required_date;
		public String shipped_date;
		public int ship_via;
		public BigDecimal freight;
		public String ship_name;
		public String ship_address;
		public String ship_city;
		public String ship_region;
		public String ship_postal_code;
		public String ship_country;
		
		@Override
		public JsonArray toJavaArray() {
			return JsonArray.of(order_id,customer_id,employee_id,order_date,required_date,shipped_date,ship_via,freight,ship_name,ship_address,ship_city,ship_region,ship_postal_code,ship_country);
		}
	}
	
	public Orders() {
		super(NorthwindTables.orders);
	}
	
	@Override
	public Record createRecord() {
		return new Record();
	}
}
