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
package org.monflabs.json.jsonpath;

import java.util.EnumSet;
import java.util.Set;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

public class MonfLabsJsonPathConfiguration {
	
	private static final Configuration CONFIGURATION = Configuration.builder()
			.jsonProvider(new MonfLabsJsonProvider())
			.mappingProvider(new MonfLabsMappingProvider())
			.options(EnumSet.noneOf(Option.class))
			.build();
	
	/**
	 * A Jayway configuration backed by the Galta JSON containers. Unlike {@link #initialize()},
	 * it does not change the JVM-wide Jayway defaults.
	 */
	public static Configuration configuration() {
		return CONFIGURATION;
	}
	
	/**
	 * Make the Galta JSON containers the JVM-wide Jayway defaults.
	 */
	public static void initialize() {
		Configuration.setDefaults(new Configuration.Defaults() {

		    private final JsonProvider jsonProvider = new MonfLabsJsonProvider();
		    private final MappingProvider mappingProvider = new MonfLabsMappingProvider();
		      
		    @Override
		    public JsonProvider jsonProvider() {
		        return jsonProvider;
		    }

		    @Override
		    public MappingProvider mappingProvider() {
		        return mappingProvider;
		    }
		    
		    @Override
		    public Set<Option> options() {
		        return EnumSet.noneOf(Option.class);
		    }
		});
	}
}
