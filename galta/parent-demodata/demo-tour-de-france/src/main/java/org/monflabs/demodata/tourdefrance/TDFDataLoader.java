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
package org.monflabs.demodata.tourdefrance;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.csv.CsvSource;
import org.monflabs.json.impexp.pojo.PojoTarget;
import org.monflabs.util.Console;
import org.monflabs.util.ResourceLoader;

public class TDFDataLoader {
	
	public static List<Tour> loadTours() {
		List<Tour> list = new ArrayList<Tour>();
		
		CsvSource source = CsvSource.newBuilder()
			.firstRowAsHeader(true)
			.reader(() -> new StringReader(ResourceLoader.loadTextResource("tourdefrance/tdf_tours.csv")))
			.build();
		
		PojoTarget<Tour> target = PojoTarget
			.<Tour>newBuilder()
			.writer( (content) -> {
				Tour t = new Tour();
				JsonObject o = (JsonObject)content.getJson();
				t.setYear(o.asInt("Year"));
				t.setDistance(o.asString("Distance"));
				t.setStarters(o.asInt("Starters"));
				t.setFinishers(o.asInt("Finishers"));
				list.add(t);
			})
			.build();
				
		source.exportTo(target);
		
		return list;
	}

	public static void main(String[] args) {
		List<Tour> l = TDFDataLoader.loadTours();
		for(Tour v: l) {
			Console.log("{0}",v);
		}
	}
}
