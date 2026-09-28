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
package org.monflabs.json.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.monflabs.util.Console;
import org.monflabs.util.PathUtil;
import org.monflabs.util.StringUtil;
import org.monflabs.util.UserPath;
import org.monflabs.util.config.ConfigException;


/**
 * 
 */ 
public class JsonFileConfig extends AbstractJsonConfig {

	public static class Builder extends ConfigBuilder<JsonFileConfig,Builder> {
		private Path folder;
		private String fileName;
		private boolean readOnly;
		private Builder() {}
		public Builder readOnly(boolean readOnly) {
			this.readOnly = readOnly;
			return this;
		}
		public Builder folder(Path folder) {
			this.folder = folder;
			return this;
		}
		public Builder fileName(String fileName) {
			this.fileName = fileName;
			return this;
		}
		@Override
		protected JsonFileConfig _build() {
			return new JsonFileConfig(this);
		}
	}
	public static Builder newBuilder() {
		return new Builder();
	}

	private Path folder;
	private Path devFolder;
	private String fileName;
	private boolean readOnly;

	private JsonFileConfig(Builder b) {
		super(b);
		this.folder = b.folder;
		this.fileName = b.fileName;
		this.readOnly = b.readOnly;
		if(folder!=null) {
			if(!Files.exists(folder)) {
				try {
					Files.createDirectories(folder);
				} catch(IOException ex) {
					Console.log(ex);
				}
			}
			// This is for development environments
			Path userHomeDev = UserPath.getUserHomeDev();
			if(userHomeDev!=null) {
				devFolder = userHomeDev.resolve(folder.getFileName().toString());
			}
		}
		load();
	}

	@Override
	public boolean isReadOnly() {
		return readOnly;
	}

	public Path getFolder() {
		return folder;
	}
	
	@Override
	public InputStream getResource(String path) {
		Path resource = getFile(path,false);
		if(resource!=null) {
			if(Files.isRegularFile(resource)) {
				try {
					return Files.newInputStream(resource);
				} catch(IOException ex) {
					throw new ConfigException(ex, "Error while loading file '{0}'",resource);
				}
			}
			throw new ConfigException(null, "Error while loading file '{0}'",resource);
		}
		return null;
	}
	@Override
	public void setResource(String path, Consumer<OutputStream> save) {
		if(isReadOnly() || folder==null) {
			throw new ConfigException(null,"Config is readonly");
		}
		Path resource = getFile(path,true);
		try  {
			Files.createDirectories(resource.getParent());
			try(OutputStream os=Files.newOutputStream(resource)) {
				save.accept(os);
			}
		} catch(Exception e) {
			throw new ConfigException(e,"Error while writing file '{0}'", resource);
		}
	}
	private Path getFile(String path, boolean forWrite) {
		if(folder!=null) {
			String fn = StringUtil.isEmpty(path) ? fileName : path;
			if(File.separatorChar!=PathUtil.POSIX_SEP) {
				fn = fn.replace(PathUtil.POSIX_SEP, File.separatorChar);
			}
			Path file = folder.resolve(fn);
			if(Files.exists(file)) {
				return file;
			}
			if(devFolder!=null) {
				Path devFile = devFolder.resolve(fn);
				if(Files.exists(devFile)) {
					return devFile;
				}
			}
			if(forWrite) {
				return file;
			}
		}
		return null;
	}
}
