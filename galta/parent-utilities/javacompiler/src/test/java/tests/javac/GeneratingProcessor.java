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
package tests.javac;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

/**
 * Annotation processor generating a source file and a resource, used to check
 * where the compiler routes the generated outputs.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class GeneratingProcessor extends AbstractProcessor {
	private boolean done;
	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if(!done) {
			done = true;
			try {
				try(Writer w = processingEnv.getFiler().createSourceFile("gen.Generated").openWriter()) {
					w.write("package gen; public class Generated { public static int value() { return 7; } }");
				}
				try(Writer w = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "gen", "info.txt").openWriter()) {
					w.write("generated");
				}
			} catch(IOException ex) {
				throw new RuntimeException(ex);
			}
		}
		return false;
	}
}
