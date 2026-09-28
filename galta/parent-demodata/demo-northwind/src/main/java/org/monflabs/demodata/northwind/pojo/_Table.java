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

import java.util.ArrayList;
import java.util.List;

public abstract class _Table<R extends _Record> {
	
	private String name;
	private List<R> records = new ArrayList<R>();
	
	public _Table(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	public List<R> getRecords() {
		return records;
	}


	public abstract _Record createRecord();
}
