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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.monflabs.json.JsonObject;
import org.monflabs.json.impexp.csv.CsvSource;
import org.monflabs.json.impexp.pojo.PojoTarget;

/**
 * Loads the Tour de France data set bundled in {@code tourdefrance/}.
 */
public class TDFDataLoader {

	/**
	 * Opens one of the bundled files, read as UTF-8 through this module's own
	 * class loader (so it works whatever loader the utilities library has).
	 *
	 * @param name a file of the {@code tourdefrance/} folder, e.g. {@code tdf_tours.csv}
	 * @throws UncheckedIOException when there is no such file
	 */
	public static Reader openResource(String name) {
		String path = "tourdefrance/"+name;
		InputStream is = TDFDataLoader.class.getClassLoader().getResourceAsStream(path);
		if(is==null) {
			throw new UncheckedIOException(new IOException("Missing resource "+path));
		}
		return new InputStreamReader(is, StandardCharsets.UTF_8);
	}

	/**
	 * The tours, one per edition, in the order of the file (by year). The
	 * editions before {@link Tour#ARCHIVE_YEAR} are flagged as archived.
	 */
	public static List<Tour> loadTours() {
		List<Tour> list = new ArrayList<Tour>();

		CsvSource source = CsvSource.newBuilder()
			.firstRowAsHeader(true)
			.reader(() -> openResource("tdf_tours.csv"))
			.build();

		PojoTarget<Tour> target = PojoTarget
			.<Tour>newBuilder()
			.writer( (content) -> {
				Tour t = new Tour();
				JsonObject o = (JsonObject)content.getJson();
				t.setYear(o.asInt("Year"));
				t.setArchived(t.getYear()<Tour.ARCHIVE_YEAR);
				t.setDistance(o.asString("Distance"));
				t.setStarters(o.asInt("Starters"));
				t.setFinishers(o.asInt("Finishers"));
				list.add(t);
			})
			.build();

		source.exportTo(target);

		return list;
	}
}
