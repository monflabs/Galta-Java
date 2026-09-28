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
package org.monflabs.galtajs.transpiler.path;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.GaltaJSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.transpiler.JSTranspiler;
import org.monflabs.galtajs.transpiler.JSTranspiler.Result;
import org.monflabs.galtajs.transpiler.JSTranspilerException;
import org.monflabs.galtajs.transpiler.JSTranspilerOptions;
import org.monflabs.util.Console;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.PathUtil;
import org.monflabs.util.StringUtil;
import org.monflabs.util.builder.Required;
import org.monflabs.util.path.FilesUtil;

public class PathTranspiler {
	
	public static final class Builder extends ObjectBuilder<PathTranspiler> {
		private JSEnvironment env;
		private JSTranspilerOptions options;
		@Required
		private Path sourceFolder;
		@Required
		private Path outputFolder;
		private String jsPackage;
		private boolean sourceFile;
		private boolean mapFile;
		@Required
		private Supplier<List<Path>> pathFactory;
		private String encoding = "UTF-8";
	    private boolean verbose;
		
		private Builder() {}
		public Builder options(JSTranspilerOptions options) {
			this.options = options;
			return this;
		}
		public Builder env(JSEnvironment env) {
			this.env = env;
			return this;
		}
		public Builder sourceFolder(Path folder) {
			this.sourceFolder = folder;
			return this;
		}
		public Builder outputFolder(Path folder) {
			this.outputFolder = folder;
			return this;
		}
		public Builder sourceFile(boolean sourceFile) {
			this.sourceFile = sourceFile;
			return this;
		}
		public Builder mapFile(boolean mapFile) {
			this.mapFile = mapFile;
			return this;
		}
		public Builder jsPackage(String jsPackage) {
			this.jsPackage = jsPackage;
			return this;
		}
		public Builder pathFactory(Supplier<List<Path>> pathFactory) {
			this.pathFactory = pathFactory;
			return this;
		}
		public Builder encoding(String encoding) {
			this.encoding = encoding;
			return this;
		}
		public Builder verbose(boolean verbose) {
			this.verbose = verbose;
			return this;
		}
		@Override
		protected PathTranspiler _build() {
			return new PathTranspiler(this);
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	private JSEnvironment env;
	private JSTranspilerOptions options;
	private Path sourceFolder;
	private Path outputFolder;
	private String jsPackage;
	private boolean sourceFile;
	private boolean mapFile;
	private Supplier<List<Path>> pathFactory;
	private String encoding;
    private boolean verbose;
    
    private PathTranspiler(Builder b) {
    	this.env = b.env;
    	this.options = b.options;
    	this.sourceFolder = b.sourceFolder;
    	this.outputFolder = b.outputFolder;
    	this.jsPackage = b.jsPackage;
    	this.sourceFile = b.sourceFile;
    	this.mapFile = b.mapFile;
    	this.pathFactory = b.pathFactory;
    	this.encoding = b.encoding;
    	this.verbose = b.verbose;
    }
    
    public void info(String msg, Object... parameters) {
    	if(verbose) {
    		Console.log(msg,parameters);
    	}
    }
    public void error(String msg, Object... parameters) {
    	Console.err(msg,parameters);
    }
    public void error(Exception ex, String msg, Object... parameters) {
    	Console.exception(ex,msg,parameters);
    }

    public List<String> execute() throws JSTranspilerException {
    	try {
	        info("[js-transpiler] SourceDir=" + sourceFolder + "  OutputDir=" + outputFolder);
	
	        // 1) Validate environment
	        if (sourceFolder == null || !Files.isDirectory(sourceFolder)) {
	            info("[js-transpiler] No source directory found; skipping.");
	            return null;
	        }
	        
	        if (outputFolder == null) {
	        	throw new JSTranspilerException(null,"OutputDirectory is null");
	        }
	        if (!Files.exists(outputFolder)) {
	        	try {
	        		Files.createDirectories(outputFolder);
	        	} catch(Exception ex) {
	        		throw new JSTranspilerException(ex,"Could not create OutputDirectory: {0}", outputFolder);
	        	}
	        }
	        if (!Files.isDirectory(outputFolder)) {
	            throw new JSTranspilerException(null,"OutputDirectory {0} is not a directory", outputFolder);
	        }
	
	        String rootPath = sourceFolder.toString();
	        
	        // 2) Scan sources
	        List<Path> paths = pathFactory.get();
	        if (paths.isEmpty()) {
	            info("[js-transpiler] No source files to compile; skipping.");
	            return null;
	        }
	        info("[js-transpiler] Found {0} source files.", paths.size());
	        
	        List<String> targetFiles = new ArrayList<String>();
	        // Class and module names are case-insensitive (lower-cased): A.js and
	        // a.js would silently overwrite each other's generated class
	        java.util.Map<String,Path> classSources = new java.util.HashMap<>();
	        for(Path source: paths) {
	            info("[js-transpiler] Transpiling " + source + ".");
	            
	            String sourcePath = PathUtil.FILE.getRelativePath(rootPath, source.toString());
	            String fullClassName = JSTranspiler.moduleNameToJavaClassName(jsPackage, sourcePath);
	            String moduleName = sourcePath.toLowerCase();
	            Path previous = classSources.putIfAbsent(fullClassName, source);
	            if(previous!=null) {
	            	throw new JSTranspilerException(null, "{0} and {1} map to the same class {2}", previous, source, fullClassName);
	            }
	            
	            Path target = outputFolder.resolve(StringUtil.replaceAll(fullClassName,'.',outputFolder.getFileSystem().getSeparator().charAt(0))+".java");
				
				if(Files.exists(target) && !Files.isRegularFile(target)) {
					error("[js-transpiler] Invalid target file {0}", target);
					return null;
				}
				
				transpile(source,target,fullClassName,moduleName);
				targetFiles.add(fullClassName);
	        }
	        
	        return targetFiles;
        } catch (Exception e) {
            throw new JSTranspilerException(e, "Compilation error: {0}", e.getMessage());
        }
    }

	private void transpile(Path source, Path target, String fullClassName, String jsModuleName) {
		String sourceCode = loadString(source);
		JSEnvironment env = this.env!=null ? this.env : GaltaJSEnvironment.create();
		// CommonJS changes how the program itself is compiled (module wrapper,
		// require/exports/module bindings), so it is a parse option, and the
		// generated class then reports isCommonJS() from the program
		int flags = options!=null && options.isCommonJS() ? JSEnvironment.SCRIPT_COMMONJS : 0;
		JSInterpretedUnit script = env.createScript(sourceCode,jsModuleName,flags);
		
		JSTranspiler transpiler = new JSTranspiler(env,options);
		Result compiled = transpiler.compileResult(fullClassName,"Object",script.getProgram(),jsModuleName);
		
		writeString(target, compiled.getJavaCode());
		if(sourceFile) {
			writeString(FilesUtil.setExtension(target,"js"), compiled.getJavaScriptCode());
		}
		if(mapFile) {
			writeString(FilesUtil.setExtension(target,"jsmap"), compiled.getTranspilerMap().serialize());
		}
	}

	private String loadString(Path f){
		if(Files.exists(f) && Files.isRegularFile(f)) {
			return FilesUtil.readString(f, encoding!=null ? Charset.forName(encoding) : null);
		}
		return null;
	}
	private void writeString(Path f, String s){
		Path dir = f.getParent();
		if(dir!=null) {
	        if (!Files.exists(dir)) {
	        	try {
	        		Files.createDirectories(dir);
	        	} catch(Exception e) {
	        		throw new JSTranspilerException(null,"Could not create directory: {0}", dir);
	        	}
	        }
	        if (!Files.isDirectory(dir)) {
	            throw new JSTranspilerException(null,"Directory {0} is not a directory", dir);
	        }
		}
		s = normalizeLineBreaks(s);
		
		try (Writer w = new OutputStreamWriter(Files.newOutputStream(f),Charset.forName(encoding))) {
			w.write(s);
		} catch(IOException ex) {
			// A missing output file must fail the build, not just be logged
			throw new JSTranspilerException(ex, "Error writing file {0}", f);
		}
	}
	public static String normalizeLineBreaks(String s) {
		if(s!=null && s.indexOf('\r')>=0) {
			// Normalize the lines breaks to make it compatible between the different platforms (Mac, Windows, ....)
			s = s.replace("\n\r", "\n");
			s = s.replace("\r\n", "\n");
			s = s.replace("\r", "\n");
		}
		return s;
	}
}
