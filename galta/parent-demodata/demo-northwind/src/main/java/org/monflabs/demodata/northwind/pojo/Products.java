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

public class Products extends _Table<Products.Record> {

	public static class Record extends _Record {
		public String product_id;
		public String product_name;
		public String supplier_id;
		public String category_id;
		public String quantity_per_unit;
		public BigDecimal unit_price;
		public int units_in_stock;
		public int units_on_order;
		public int reorder_level;
		public boolean discontinued;
		
		@Override
		public JsonArray toJavaArray() {
			return JsonArray.of(product_id,product_name,supplier_id,category_id,quantity_per_unit,unit_price,units_in_stock,units_on_order,reorder_level,discontinued);
		}
	}
	
	public Products() {
		super(NorthwindTables.products);
	}
	
	@Override
	public Record createRecord() {
		return new Record();
	}
}
