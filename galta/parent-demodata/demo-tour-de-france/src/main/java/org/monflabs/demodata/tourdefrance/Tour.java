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

public class Tour {
	
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
	public void setYear(int year) {
		this.year = year;
		this.archived = year<1910;
	}

	public String getDistance() {
		return distance;
	}
	public void setDistance(String distance) {
		this.distance = distance;
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
