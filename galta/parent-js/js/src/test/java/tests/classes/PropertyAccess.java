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
package tests.classes;

/**
 * Class to test property access
 * @author Philippe Riand
 */
public class PropertyAccess {

	private String pr = "p-pr";
	private String prw = "p-prw";
	private String prw2 = "p-prw2";
	
	public String getPr() {
		return pr;
	}
	
	public String getPrw() {
		return prw;
	}
	public void setPrw(String v) {
		prw = v;
	}

	public String getPrw2() {
		return prw2;
	}
	public void setPrw2(String v, Object p2) {
		prw2 = v;
	}
	
	public void getVoidProp() {
	}	

	public String getParamProp(Object o) {
		return null;
	}	
}
