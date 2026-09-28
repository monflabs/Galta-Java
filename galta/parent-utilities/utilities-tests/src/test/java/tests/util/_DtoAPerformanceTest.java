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

import org.apache.harmony.luni.util.NumberConverter;
import org.monflabs.util.DtoA;
import org.monflabs.util.performance.PerformanceWatch;
import org.mozilla.javascript.RhinoDtoa;
import org.yuanheng.cookjson.DoubleUtils;

import info.adams.ryu.RyuDouble;
import tests.ProjectTestCase;

public class _DtoAPerformanceTest extends ProjectTestCase {
	
	// Only run when needed, not as reg unit tests
	private static final boolean RUN = false;

	public void testPerformance() {
		if(!RUN) {
			return;
		}
		// Warm up
		doubleConv(1000, 1234.5678);
		stringFormatConv(1000, 1234.5678);
		dtoaStandardConv(1000, 1234.5678);
		dtoaRhinoStandardConv(1000, 1234.5678);

		PerformanceWatch wDouble = new PerformanceWatch("JVM Double conv");
		PerformanceWatch wFormat = new PerformanceWatch("JVM String.format conv");
		PerformanceWatch wDtoaStandard = new PerformanceWatch("DecimalFormat dtoa ");
		PerformanceWatch wDtoaRhinoStandard = new PerformanceWatch("DtoA Rhino standard conv");
		PerformanceWatch wDtoaRhinoFtoa = new PerformanceWatch("DtoA Rhino ftoa conv");
		PerformanceWatch wRyu = new PerformanceWatch("Ryu conv");
		PerformanceWatch wApacheHarmony = new PerformanceWatch("Apache Harmony");
		PerformanceWatch wCookJson = new PerformanceWatch("Cookjson");

		// The test with different values
		int VALUES = 10;
		int LOOP = 100000;
		//for(Double VALUE: Arrays.asList(12.0,1234.56789,3456.256E34)) {
		for(int values=0; values<VALUES; values++) {
			double VALUE = values>VALUES/2 
					? Math.floor(Math.random()*1_000_000) // Use an integer
					: Math.random()*1_000_000;
			support.print("VALUE = {0}", VALUE);
			
			wDouble.run( () -> {
				doubleConv(LOOP,VALUE);
			});
	
			wFormat.run( () -> {
				stringFormatConv(LOOP,VALUE);
			});
	
			wDtoaStandard.run( () -> {
				dtoaStandardConv(LOOP,VALUE);
			});
	
			wDtoaRhinoStandard.run( () -> {
				dtoaRhinoStandardConv(LOOP,VALUE);
			});
			
			wDtoaRhinoFtoa.run( () -> {
				dtoaRhinoFtoaConv(LOOP,VALUE);
			});
			
			wRyu.run( () -> {
				dtoaRyuConv(LOOP,VALUE);
			});
			
			wApacheHarmony.run( () -> {
				apacheHarmony(LOOP,VALUE);
			});
			
			wCookJson.run( () -> {
				cookJson(LOOP,VALUE);
			});
		}
		
		support.print("=====================================================");
		wDouble.dump();
		wFormat.dump();
		wDtoaStandard.dump();
		wDtoaRhinoStandard.dump();
		wDtoaRhinoFtoa.dump();
		wRyu.dump();
		wApacheHarmony.dump();
		wCookJson.dump();
	}

	private void doubleConv(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			Double.toString(VALUE);
		}
	}
	private void stringFormatConv(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			String.format("%.4f", VALUE);
		}
	}
	private void dtoaStandardConv(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			DtoA.toStandard(VALUE);
		}
	}
	private void dtoaRhinoStandardConv(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			RhinoDtoa.standard(VALUE);
		}
	}
	private void dtoaRhinoFtoaConv(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			RhinoDtoa.ftoa(VALUE);
		}
	}
	private void dtoaRyuConv(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			RyuDouble.doubleToString(VALUE);
		}
	}
	private void apacheHarmony(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			NumberConverter.convert(VALUE);
		}
	}
	private void cookJson(int loop, double VALUE) {
		for(int i=0; i<loop; i++) {
			DoubleUtils.toString(VALUE);
		}
	}
}
