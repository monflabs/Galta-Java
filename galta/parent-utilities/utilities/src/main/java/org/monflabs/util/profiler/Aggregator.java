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
package org.monflabs.util.profiler;

import java.io.PrintStream;
import java.util.List;


/**
 * Profiler aggregator.
 */
public interface Aggregator {

    public Aggregator getParent();
    public String getType();
    public String getParam();
    
    public List<Aggregator> getChildren();
    
    public int getCount();
    
    public long getMinCpuTime();
    public long getMaxCpuTime();
    public long getTotalCpuTime();
    public long getChildrenCpuTime();
    public long getSpecificCpuTime();
    public long getAvgCpuTime();

    public long getMinWallTime();
    public long getMaxWallTime();
    public long getTotalWallTime();
    public long getChildrenWallTime();
    public long getSpecificWallTime();
    public long getAvgWallTime();

    void dump(PrintStream pw);
}
