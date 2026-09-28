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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.monflabs.json.JsonArray;
import org.monflabs.json.JsonFactory;

public class MiniJsonDataSet {

	private static final String RESOURCE_FOLDER = "mini-dataset/dvdrental/";

	private JsonArray actors;
	private JsonArray categories;
	private JsonArray films_actors;
	private JsonArray films_actors_direct;
	private JsonArray films_categories;
	private JsonArray films;
	private JsonArray languages;
	
	public MiniJsonDataSet(JsonFactory factory) {
		this.actors = parse(factory,Schema.COLLECTIONS.Actors);
		this.categories = parse(factory,Schema.COLLECTIONS.Categories);
		this.films_actors = parse(factory,Schema.COLLECTIONS.Films_actors);
		this.films_actors_direct= parse(factory,Schema.COLLECTIONS.Films_actors_direct);
		this.films_categories = parse(factory,Schema.COLLECTIONS.Films_categories);
		this.films = parse(factory,Schema.COLLECTIONS.Films);
		this.languages = parse(factory,Schema.COLLECTIONS.Languages);
	}
	
	JsonArray parse(JsonFactory factory, Schema.COLLECTIONS collection) {
		String resource = RESOURCE_FOLDER+collection.getResourceName();
		InputStream is = MiniJsonDataSet.class.getClassLoader().getResourceAsStream(resource);
		if(is==null) {
			throw new IllegalStateException("Missing resource "+resource);
		}
		try (Reader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			return (JsonArray)factory.parse(r);
		} catch(IOException ex) {
			throw new IllegalStateException("Can't read resource "+resource, ex);
		}
	}
	
	public JsonArray getActors() {
		return actors;
	}

	public JsonArray getCategories() {
		return categories;
	}

	public JsonArray getFilms_actors_direct() {
		return films_actors_direct;
	}

	public JsonArray getFilms_actors() {
		return films_actors;
	}

	public JsonArray getFilms_categories() {
		return films_categories;
	}

	public JsonArray getFilms() {
		return films;
	}

	public JsonArray getLanguages() {
		return languages;
	}

//	public static void main(String[] args) {
//		MiniJsonDataSet md = new MiniJsonDataSet(JsonFactory.get());
//		System.out.println(md.getActors().toString());
//	}
}
