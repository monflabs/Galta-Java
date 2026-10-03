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
package org.monflabs.galtajs.rt.transpiler;

/**
 * A region of a transpiled function: statements the transpiler moved out of a
 * method that would otherwise be too large for the JIT (see
 * TranspilerMethodSplitter). It is emitted as an anonymous class, whose run()
 * method javac compiles separately, and run at once.
 * <p>
 * It returns {@link #NORMAL} when its statements complete normally, one of
 * {@link #JUMPS} for a {@code break} or {@code continue} that leaves it (the
 * caller then performs it), and otherwise the value of a JavaScript
 * {@code return}.
 */
@FunctionalInterface
public interface JSTranspiledRegion {

	/**
	 * The statements of the region completed normally.
	 */
	public static final Object NORMAL = new Object() {
		@Override
		public String toString() {
			return "JSTranspiledRegion.NORMAL";
		}
	};

	/**
	 * A return of a derived class constructor: its value is staged in the
	 * constructor's return holder, and the constructor breaks to its return label.
	 */
	public static final Object DERIVED_RETURN = new Object() {
		@Override
		public String toString() {
			return "JSTranspiledRegion.DERIVED_RETURN";
		}
	};

	/**
	 * A break or continue leaving the region, by index.
	 */
	public static final Object[] JUMPS = Jumps.create(64);

	public Object run();

	final class Jumps {
		private Jumps() {
		}
		static Object[] create(int count) {
			Object[] jumps = new Object[count];
			for(int i=0; i<count; i++) {
				int index = i;
				jumps[i] = new Object() {
					@Override
					public String toString() {
						return "JSTranspiledRegion.JUMPS["+index+"]";
					}
				};
			}
			return jumps;
		}
	}
}
