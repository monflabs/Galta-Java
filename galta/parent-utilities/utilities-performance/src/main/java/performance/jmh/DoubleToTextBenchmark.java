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
package performance.jmh;

import java.util.concurrent.TimeUnit;

import org.monflabs.util.DtoA;
import org.mozilla.javascript.RhinoDtoa;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.yuanheng.cookjson.DoubleUtils;

import info.adams.ryu.RyuDouble;

/**
 * Converting a double to text: Galta's DtoA (JavaScript Number::toString) compared with the
 * JDK and the reference implementations kept in the utilities-tests sources. The Apache
 * Harmony converter is not included: it calls a native method for the large and small values.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DoubleToTextBenchmark {

	// An integral value, a decimal, a large and a small one written with an exponent
	@Param({"123456.0", "1234.5678", "3.456256E37", "1.2345E-9"})
	public String value;

	private double d;

	@Setup
	public void setup() {
		d = Double.parseDouble(value);
	}

	@Benchmark
	public String galtaDtoA() {
		return DtoA.toStandard(d);
	}

	@Benchmark
	public String jdkDoubleToString() {
		return Double.toString(d);
	}

	@Benchmark
	public String jdkStringFormat() {
		return String.format("%.4f", d);
	}

	@Benchmark
	public String rhinoStandard() {
		return RhinoDtoa.standard(d);
	}

	@Benchmark
	public String rhinoFtoa() {
		return RhinoDtoa.ftoa(d);
	}

	@Benchmark
	public String ryu() {
		return RyuDouble.doubleToString(d);
	}

	@Benchmark
	public String cookjson() {
		return DoubleUtils.toString(d);
	}
}
