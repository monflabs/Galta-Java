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
package org.monflabs.galtajs.maven;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.galtajs.transpiler.path.PathTranspiler;

@Mojo(
        name = "generate-sources",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
/**
 * Transpile the JavaScript files of a project to Java sources, added to the
 * project's compile source roots.
 */
public class JSTranspilerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${basedir}/js", required = true)
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/js", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "js", required = true)
    private String jsPackage;

    @Parameter
    private List<String> includes;

    @Parameter
    private List<String> excludes;
    
    @Parameter
    private boolean sourceMap=false;

    @Parameter
    private boolean sourceCode=false;
    
    @Parameter
    private boolean mapFile=true;

    @Parameter
    private boolean sourceFile=true;
    
    @Parameter
    private boolean sourceInComments=false;
    
    @Parameter
    private int maxSourceInComments=JSTranspilerOptions.DEFAULT_SOURCE_INCOMMENTS;
    
    @Parameter
    private boolean splitCode=false;
    
    /**
     * Transpile the files as CommonJS modules.
     * Note: passed to {@link JSTranspilerOptions}, which the transpiler does not read yet.
     */
    @Parameter
    private boolean commonJS=false;

    @Parameter(property = "project.build.sourceEncoding", defaultValue = "UTF-8")
    private String encoding;

    /**
     * Fail the build when a file cannot be transpiled; otherwise the error is only logged.
     */
    @Parameter(defaultValue = "true")
    private boolean failOnError;

    @Parameter(defaultValue = "true")
    private boolean followSymlinks;

    @Parameter
    private boolean galtaJs=false;

    @Parameter
    private boolean verbose=false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
    	try {
    		JSEnvironment env = JSEnvironment.newBuilder()
    				.configure( (b) -> { if(galtaJs) b.enableGaltaJSExtensions(); } )
    				.scriptOptimizer(ScriptOptimizer.defaultOptimizer())
    				.build();
    		
    		JSTranspilerOptions options = JSTranspilerOptions.newBuilder()
    				.sourceMap(sourceMap)
    				.sourceCode(sourceCode)
    				.sourceInComments(sourceInComments)
    				.maxSourceInComments(maxSourceInComments)
    				.splitCode(splitCode)
    				.commonJS(commonJS)
    				.build();
    		
    		PathTranspiler transpiler = PathTranspiler.newBuilder()
    				.env(env)
    				.options(options)
    				.sourceFolder(sourceDirectory.toPath())
    				.outputFolder(outputDirectory.toPath())
    				.jsPackage(jsPackage)
    				.sourceFile(sourceFile)
    				.mapFile(mapFile)
    				.pathFactory( () -> {
    					return scanJavaSources();
    				})
    				.encoding(encoding)
    				.verbose(verbose)
    				.build();
    		
    		transpiler.execute();
        } catch (Exception e) {
        	if(!failOnError) {
        		getLog().warn("JavaScript transpilation failed (failOnError=false): " + e.getMessage(), e);
        		return;
        	}
        	getLog().error("JavaScript transpilation failed", e);
        	if(e instanceof MojoExecutionException) {
        		throw (MojoExecutionException)e;
        	}
            throw new MojoExecutionException("Transpilation error: " + e.getMessage(), e);
        }
    	// The generated sources are compiled with the project's own sources
    	if(project!=null) {
    		project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
    	}
    }
	
	
    private List<Path> scanJavaSources() {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDirectory);
        //scanner.setCaseSensitive(caseSensitive);
        scanner.setFollowSymlinks(followSymlinks);

        if (includes == null || includes.isEmpty()) {
            scanner.setIncludes(new String[]{"**/*.js"});
        } else {
            scanner.setIncludes(includes.toArray(new String[0]));
        }
        if (excludes != null && !excludes.isEmpty()) {
            scanner.setExcludes(excludes.toArray(new String[0]));
        }
        scanner.addDefaultExcludes();
        scanner.scan();

        String[] files = scanner.getIncludedFiles(); // relative paths
        return Arrays.stream(files)
                .map(rel -> sourceDirectory.toPath().resolve(rel))
                .collect(Collectors.toList());
    }
}
