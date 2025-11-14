/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.api.reporter;

import java.io.Closeable;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.plugins.TikaPlugin;

/**
 * This is called asynchronously by the AsyncProcessor. This
 * is not thread safe, and implementers must be careful to implement
 * {@link #report(FetchEmitTuple, PipesResult, long)} in a thread safe
 * way.
 * <p/>
 * Note, however, that this is not called in the forked processes.
 * Implementers do not have to worry about synchronizing across processes;
 * for example, one could use an in-memory h2 database as a target.
 */
public interface PipesReporter extends Closeable, TikaPlugin {

    //Implementers are responsible for preventing reporting after
    //crashes if that is the desired behavior.
    public abstract void report(FetchEmitTuple t, PipesResult result, long elapsed);


    /**
     * Make sure to override {@link #supportsTotalCount()}
     * to return <code>true</code>
     * @param totalCountResult
     */
    void report(TotalCountResult totalCountResult);

    /**
     * Override this if your reporter supports total count.
     * @return <code>false</code> as the baseline implementation
     */
    boolean supportsTotalCount();

    /**
     * This is called if the process has crashed.
     * Implementers should not rely on close() to be called after this.
     * @param t
     */
    void error(Throwable t);
    /**
     * This is called if the process has crashed.
     * Implementers should not rely on close() to be called after this.
     * @param msg
     */
    void error(String msg);

}
