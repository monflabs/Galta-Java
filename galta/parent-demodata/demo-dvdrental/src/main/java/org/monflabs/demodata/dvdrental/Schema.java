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
package org.monflabs.demodata.dvdrental;

/**
 * The collections of the DVD rental mini dataset.
 */
public class Schema {
	
	/**
	 * A collection, and the JSON resource holding it.
	 */
	public enum COLLECTIONS {
		Actors("actors.json"),
		Categories("categories.json"),
		Films_actors("films_actors.json"),
		Films_actors_direct("films_actors-direct.json"),
		Films_categories("films_categories.json"),
		Films("films.json"),
		Languages("languages.json");
		
		private final String resourceName;
		
		COLLECTIONS(String resourceName) {
			this.resourceName = resourceName;
		}
		
		/**
		 * The resource file name, relative to the dataset folder.
		 */
		public String getResourceName() {
			return resourceName;
		}
	}
}
