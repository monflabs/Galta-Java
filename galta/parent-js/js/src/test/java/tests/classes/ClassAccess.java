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
 * Class to test access
 * @author Philippe Riand
 */
public class ClassAccess {

	public static String S1 = "StaticField1";
	public static String S2() {
		return "StaticMethod2";
	}
	
	public String I1 = "InstanceField1";
	public String I2() {
		return "InstanceMethod2";
	}
}
