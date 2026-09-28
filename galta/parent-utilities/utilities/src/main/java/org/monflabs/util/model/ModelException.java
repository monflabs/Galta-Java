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
package org.monflabs.util.model;

import org.monflabs.util.BaseException;


/**
 * @author Philippe Riand
 */
public class ModelException extends BaseException  {

	private static final long serialVersionUID = 1L;

    public ModelException(Throwable nextException) {
        super(nextException,nextException!=null?nextException.getClass().getSimpleName()+": "+nextException.getLocalizedMessage():null);
    }

    public ModelException(Throwable nextException, String msg, Object... parameters) {
        super(nextException,msg,parameters);
    }
    
	public static ModelException wrap(Throwable e) {
		return e instanceof ModelException ? (ModelException)e : new ModelException(e);
	}
}
