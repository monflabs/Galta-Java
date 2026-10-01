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

import org.monflabs.json.JsonArray;

/**
 * One edition of the Tour de France, as read from {@code tourdefrance/tdf_tours.csv}.
 */
public class Tour {

	/**
	 * The editions before this year are flagged as archived by {@link TDFDataLoader}.
	 */
	public static final int ARCHIVE_YEAR = 1910;

	private int year;
	private String distance;
	private int starters;
	private int finishers;

	private boolean archived;

	public Tour() {
	}

	public int getYear() {
		return year;
	}
	/**
	 * Sets the year only: the archived flag is a separate property (it used
	 * to be recomputed here, silently overwriting a value set before).
	 */
	public void setYear(int year) {
		this.year = year;
	}

	/**
	 * The distance as published, e.g. {@code "2,428 km (1,509 mi)"} - the
	 * separators between the numbers and the units are non-breaking spaces.
	 *
	 * @see #getDistanceKm()
	 */
	public String getDistance() {
		return distance;
	}
	public void setDistance(String distance) {
		this.distance = distance;
	}

	/**
	 * The distance in kilometers, parsed from {@link #getDistance()}, or
	 * {@code Double.NaN} when there is none or it cannot be read.
	 */
	public double getDistanceKm() {
		return parseKm(distance);
	}

	/**
	 * Reads the leading number of a distance like {@code "2,428.5 km (1,509 mi)"},
	 * ignoring the thousands separators.
	 */
	static double parseKm(String distance) {
		if(distance==null) {
			return Double.NaN;
		}
		StringBuilder b = new StringBuilder();
		for(int i=0; i<distance.length(); i++) {
			char c = distance.charAt(i);
			if((c>='0' && c<='9') || c=='.') {
				b.append(c);
			} else if(c==',' && b.length()>0) {
				// thousands separator
			} else if(b.length()>0 || !Character.isSpaceChar(c)) {
				break;
			}
		}
		if(b.length()==0) {
			return Double.NaN;
		}
		try {
			return Double.parseDouble(b.toString());
		} catch(NumberFormatException e) {
			return Double.NaN;
		}
	}

	public int getStarters() {
		return starters;
	}
	public void setStarters(int starters) {
		this.starters = starters;
	}

	public int getFinishers() {
		return finishers;
	}
	public void setFinishers(int finishers) {
		this.finishers = finishers;
	}

	/**
	 * Whether this is an old edition (before {@link #ARCHIVE_YEAR}): set by
	 * the loader, a plain property otherwise.
	 */
	public boolean isArchived() {
		return archived;
	}
	public void setArchived(boolean archived) {
		this.archived = archived;
	}

	@Override
	public String toString() {
		return JsonArray.of(year,distance,starters,finishers).stringify(true);
	}
}
