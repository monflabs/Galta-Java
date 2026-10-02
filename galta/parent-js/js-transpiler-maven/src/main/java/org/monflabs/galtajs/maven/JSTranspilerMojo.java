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
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.optimizer.ScriptOptimizer;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.galtajs.transpiler.path.PathTranspiler;
import org.monflabs.util.PathUtil;
import org.monflabs.util.StringUtil;

/**
 * Transpile the JavaScript files of a project to Java sources, added to the
 * project's compile source roots.
 * <p>
 * The build is incremental: a file is transpiled again only when one of its
 * outputs is missing or older than the file, or when the configuration, the
 * plugin or the GaltaJS engine changed (recorded in a stamp file in the output
 * directory). Generated files whose JavaScript source is gone are deleted when
 * the output directory is inside the project's build directory.
 */
@Mojo(
        name = "generate-sources",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        threadSafe = true
)
public class JSTranspilerMojo extends AbstractMojo {

	static final String STAMP_FILE = ".galtajs-transpiler.stamp";

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
     * Transpile the files as CommonJS modules: each program is compiled with
     * the CommonJS module wrapper ({@code require}, {@code exports} and
     * {@code module} bindings).
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

    /**
     * Skip the transpilation entirely (the generated sources are not added
     * to the project either).
     */
    @Parameter(property = "galtajs.transpiler.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
    	if(skip) {
    		getLog().info("JavaScript transpilation skipped");
    		return;
    	}
    	try {
    		transpile();
        } catch (Exception e) {
        	if(!failOnError) {
        		getLog().warn("JavaScript transpilation failed (failOnError=false): " + e.getMessage(), e);
        		return;
        	}
        	if(e instanceof MojoExecutionException me) {
        		throw me;
        	}
        	getLog().error("JavaScript transpilation failed", e);
            throw new MojoExecutionException("Transpilation error: " + e.getMessage(), e);
        }
    	// The generated sources are compiled with the project's own sources
    	if(project!=null) {
    		project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
    	}
    }

    private void transpile() throws Exception {
    	if(!sourceDirectory.isDirectory()) {
    		getLog().info("No JavaScript source directory " + sourceDirectory + ": nothing to transpile");
    		return;
    	}
    	Path outputRoot = outputDirectory.toPath();
    	List<Path> sources = scanJavaSources();

    	// The files generated for each source, as PathTranspiler names them
    	Map<Path,List<Path>> outputs = new LinkedHashMap<>();
    	Map<String,Path> classSources = new HashMap<>();
    	String rootPath = sourceDirectory.toPath().toString();
    	for(Path source: sources) {
            String sourcePath = PathUtil.FILE.getRelativePath(rootPath, source.toString());
            String fullClassName = JSTranspiler.moduleNameToJavaClassName(jsPackage, sourcePath);
            Path previous = classSources.putIfAbsent(fullClassName, source);
            if(previous!=null) {
            	throw new MojoExecutionException(previous + " and " + source + " map to the same class " + fullClassName);
            }
            Path java = outputRoot.resolve(StringUtil.replaceAll(fullClassName,'.',File.separatorChar)+".java");
            List<Path> files = new ArrayList<>();
            files.add(java);
            if(sourceFile) {
            	files.add(withExtension(java, "js"));
            }
            if(mapFile) {
            	files.add(withExtension(java, "jsmap"));
            }
            for(Path f: files) {
            	if(Files.exists(f) && !Files.isRegularFile(f)) {
            		throw new MojoExecutionException("Invalid target file " + f + ": it exists and is not a regular file");
            	}
            }
            outputs.put(source, files);
    	}

    	deleteOrphans(outputRoot, outputs);

    	String fingerprint = fingerprint();
    	// Next to the output folder, not in it: the output folder is a source root, packaged
    	// in the sources jar, and the stamp holds local paths
    	Path stamp = outputRoot.toAbsolutePath().resolveSibling(outputRoot.getFileName()+STAMP_FILE);
    	boolean sameConfiguration = Files.isRegularFile(stamp)
    			&& fingerprint.equals(Files.readString(stamp, StandardCharsets.UTF_8));
    	List<Path> stale = new ArrayList<>();
    	for(Map.Entry<Path,List<Path>> e: outputs.entrySet()) {
    		if(!sameConfiguration || isStale(e.getKey(), e.getValue())) {
    			stale.add(e.getKey());
    		}
    	}
    	if(stale.isEmpty()) {
    		getLog().info("JavaScript transpilation: " + sources.size() + " file(s) up to date");
    		return;
    	}
    	getLog().info("Transpiling " + stale.size() + " of " + sources.size() + " JavaScript file(s) to " + outputDirectory);

    	// A failure part way must not leave a stamp vouching for half-written outputs
    	Files.deleteIfExists(stamp);

		JSEnvironment env = JSEnvironment.newBuilder()
				.configure( (b) -> { if(galtaJs) b.enableGaltaJSExtensions(); } )
				.scriptOptimizer(ScriptOptimizer.defaultOptimizer())
				.build();

		PathTranspiler transpiler = PathTranspiler.newBuilder()
				.env(env)
				.options(transpilerOptions())
				.sourceFolder(sourceDirectory.toPath())
				.outputFolder(outputRoot)
				.jsPackage(jsPackage)
				.sourceFile(sourceFile)
				.mapFile(mapFile)
				.pathFactory( () -> stale )
				.encoding(encoding)
				.verbose(verbose)
				.build();

		List<String> generated = transpiler.execute();
		// null: PathTranspiler gave up without an exception (e.g. an invalid target)
		if(generated==null || generated.size()!=stale.size()) {
			throw new MojoExecutionException("The JavaScript transpilation did not produce the expected classes ("
					+ (generated==null ? 0 : generated.size()) + " of " + stale.size() + ")");
		}
		Files.createDirectories(outputRoot);
		Files.createDirectories(stamp.getParent());
		Files.writeString(stamp, fingerprint, StandardCharsets.UTF_8);
    }

    private JSTranspilerOptions transpilerOptions() {
    	return JSTranspilerOptions.newBuilder()
				.sourceMap(sourceMap)
				.sourceCode(sourceCode)
				.sourceInComments(sourceInComments)
				.maxSourceInComments(maxSourceInComments)
				.splitCode(splitCode)
				.commonJS(commonJS)
				.build();
    }

    private static Path withExtension(Path java, String ext) {
    	String name = java.getFileName().toString();
    	return java.resolveSibling(name.substring(0, name.length()-".java".length()) + "." + ext);
    }

    private static boolean isStale(Path source, List<Path> outputs) throws IOException {
    	long sourceTime = Files.getLastModifiedTime(source).toMillis();
    	for(Path f: outputs) {
    		if(!Files.isRegularFile(f) || Files.getLastModifiedTime(f).toMillis()<sourceTime) {
    			return true;
    		}
    	}
    	return false;
    }

    /**
     * What the generated code depends on besides the sources: the
     * configuration, and the plugin and engine builds (a new snapshot of
     * either regenerates everything).
     */
    private String fingerprint() {
    	return String.join("\n",
    			"jsPackage=" + jsPackage,
    			"sourceMap=" + sourceMap,
    			"sourceCode=" + sourceCode,
    			"mapFile=" + mapFile,
    			"sourceFile=" + sourceFile,
    			"sourceInComments=" + sourceInComments,
    			"maxSourceInComments=" + maxSourceInComments,
    			"splitCode=" + splitCode,
    			"commonJS=" + commonJS,
    			"galtaJs=" + galtaJs,
    			"encoding=" + encoding,
    			"plugin=" + buildOf(JSTranspilerMojo.class),
    			"engine=" + buildOf(JSEnvironment.class)) + "\n";
    }

    private static String buildOf(Class<?> c) {
    	try {
    		CodeSource cs = c.getProtectionDomain().getCodeSource();
    		if(cs!=null && cs.getLocation()!=null) {
    			Path p = Path.of(cs.getLocation().toURI());
    			if(Files.isDirectory(p)) {
    				// target/classes in a reactor build: its newest class file
    				try (Stream<Path> files = Files.walk(p)) {
    					return p + "@" + files.mapToLong(f -> f.toFile().lastModified()).max().orElse(0);
    				}
    			}
    			return p + "@" + Files.getLastModifiedTime(p).toMillis();
    		}
    	} catch(IOException | URISyntaxException | RuntimeException e) {
    		// unknown: compared as is
    	}
    	return "?";
    }

    /**
     * Deletes the generated files (.java, .js, .jsmap) left by sources that no
     * longer exist - only in an output directory inside the project's build
     * directory, which this plugin owns, never in a source tree.
     */
    private void deleteOrphans(Path outputRoot, Map<Path,List<Path>> outputs) throws IOException {
    	if(!Files.isDirectory(outputRoot)) {
    		return;
    	}
    	if(project==null || project.getBuild()==null || project.getBuild().getDirectory()==null
    			|| !outputRoot.toAbsolutePath().normalize().startsWith(Path.of(project.getBuild().getDirectory()).toAbsolutePath().normalize())) {
    		getLog().debug("Output directory outside the build directory: generated files are never deleted");
    		return;
    	}
    	Set<Path> expected = new HashSet<>();
    	for(List<Path> files: outputs.values()) {
    		for(Path f: files) {
    			expected.add(f.toAbsolutePath().normalize());
    		}
    	}
    	List<Path> orphans;
    	try (Stream<Path> files = Files.walk(outputRoot)) {
    		orphans = files
    				.filter(Files::isRegularFile)
    				.filter(f -> {
    					String name = f.getFileName().toString();
    					return name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".jsmap");
    				})
    				.filter(f -> !expected.contains(f.toAbsolutePath().normalize()))
    				.collect(Collectors.toList());
    	}
    	for(Path f: orphans) {
    		getLog().info("Deleting " + f + ": its JavaScript source is gone");
    		Files.delete(f);
    	}
    }

    private List<Path> scanJavaSources() {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDirectory);
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
