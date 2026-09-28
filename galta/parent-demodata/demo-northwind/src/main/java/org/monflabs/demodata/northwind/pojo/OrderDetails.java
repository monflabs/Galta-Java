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

public class OrderDetails extends _Table<OrderDetails.Record> {

	public static class Record extends _Record {
		public String order_id;
		public String product_id;
		public BigDecimal unit_price;
		public int quantity;
		public BigDecimal discount;
		
		@Override
		public JsonArray toJavaArray() {
			return JsonArray.of(order_id,product_id,unit_price,quantity,discount);
		}
	}
	
	public OrderDetails() {
		super(NorthwindTables.order_details);
	}
	
	@Override
	public Record createRecord() {
		return new Record();
	}
}
