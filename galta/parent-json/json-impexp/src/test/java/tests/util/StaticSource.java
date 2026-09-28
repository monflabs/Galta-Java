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
package tests.util;

import java.time.Instant;
import java.util.Iterator;

import org.monflabs.json.JsonFactory;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.impl.JsonSourceImpl;
import org.monflabs.json.impexp.util.StaticContent;
import org.monflabs.json.impexp.util.StaticDeletedContent;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.iterators.Iterators;

public class StaticSource extends JsonSourceImpl {
	
	public static class DataContent extends StaticContent {
		public DataContent(String collection, String key, String json) {
			this(collection,key,json,null);
		}
		public DataContent(String collection, String key, String json, Instant timestamp) {
			super(JsonKey.of(collection,key),JsonFactory.get().parse(json),timestamp);
		}
	}
	public static class DeletedContent extends StaticDeletedContent {
		public DeletedContent(String collection, String key) {
			super(JsonKey.of(collection,key));
		}
	}
	
	private static JsonContent[] DEFAULT_CONTENT = new JsonContent[] {
		new DataContent("col1","k11","{a:'v11', b: 1}"),
		new DataContent("col1","k12","{a:'v12', b: 1}"),
		new DataContent("col1","k13","{a:'v13', b: 1}"),
		new DataContent("col1","k14","{a:'v14', b: 1}"),
		new DataContent("col2","k21","{a:'v21', b: 2}"),
		new DataContent("col2","k22","{a:'v22', b: 2}"),
		new DataContent("col2","k23","{a:'v23', b: 2}"),
		new DataContent("col2","k24","{a:'v24', b: 2}"),
		new DataContent("col2","k25","{a:'v25', b: 2}"),
		new DataContent("col2","k26","{a:'v26', b: 2}"),
		new DataContent(null,"k31","{a:'v31', b: 3}"),
		new DataContent(null,"*desneiges","{a:'v32', b: 3}"),
		//new Content("你好","{a:'Nǐ hǎo', b: 3}"), unicode encoded bellow because of the editor...
		new DataContent(null,"\u4f60\u597d","{a:'N\u01d0 h\u01ceo', b: 3}"),
	};
	private static JsonContent[] CREATED_CONTENT = new JsonContent[] {
		new DataContent("col","k01","{a:'v1'}"),
		new DataContent("col","k02","{a:'v2'}"),
		new DataContent("col","k03","{a:'v3'}"),
		new DataContent("col","k04","{a:'v4'}"),
		new DataContent("col","k05","{a:'v5'}"),
		new DataContent("col","k20","{a:'v20'}"),
	};
	private static JsonContent[] DELETED_CONTENT = new JsonContent[] {
		new DeletedContent("col","k02"),
		new DeletedContent("col","k03"),
	};
	private static JsonContent[] MIXED_CONTENT = new JsonContent[] {
		new DataContent("col","k06","{a:'v6'}"),
		new DataContent("col","k07","{a:'v7'}"),
		new DeletedContent("col","k04"),
		new DataContent("col","k08","{a:'v8'}"),
		new DeletedContent("col","k05"),
		new DataContent("col","k09","{a:'v9'}"),
	};
	
	public static enum CONTENT {
		DEFAULT(DEFAULT_CONTENT),
		CREATED(CREATED_CONTENT),
		DELETED(DELETED_CONTENT),
		MIXED(MIXED_CONTENT)
		;
		private JsonContent[] jsonContent;
		CONTENT(JsonContent[] jsonContent) {
			this.jsonContent = jsonContent;
		}
		public JsonContent[] getJsonContent() {
			return jsonContent;
		}
	}

	public static class Builder extends ObjectBuilder<StaticSource> {
		private CONTENT content = CONTENT.DEFAULT;

		private Builder() {}

		public Builder content(CONTENT content) {
			this.content = content;
			return this;
		}
		@Override
		protected StaticSource _build() {
			return new StaticSource(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	
	private CONTENT content;
	
	protected StaticSource(Builder builder) {
		this.content = builder.content;
	}

	@Override
	public long estimatedCount() {
		return this.content.getJsonContent().length;
	}

	@Override
	protected Iterator<JsonContent> createJsonContentIterator() {
		return Iterators.array(content.getJsonContent());
	}
}
