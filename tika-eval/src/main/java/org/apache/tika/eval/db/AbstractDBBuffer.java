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
package org.apache.tika.eval.db;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Abstract buffer for map of values and unique ids.
 * <p>
 * Use this for fast in memory lookups of smallish sets of values.
 *
 */
abstract class AbstractDBBuffer {

    private final Map<String, Integer> m = new HashMap<>();
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();

    private int numWrites = 0;

    public int getId(String key) {
        r.lock();
        try {
            Integer v = m.get(key);
            if (v != null) {
                return v;
            }
        } finally {
            r.unlock();
        }

        try {
            w.lock();
            Integer v = m.get(key);
            if (v != null) {
                return v;
            }
            v = m.size()+1;
            m.put(key, v);
            write(v, key);
            numWrites++;
            return v;
        } finally {
            w.unlock();
        }
    }

    public int getNumWrites() {
        return numWrites;
    }

    //Odd to throw RuntimeException, I know.  It should be
    //catastrophic if this buffer can't write to the db.
    public abstract void write(int id, String value) throws RuntimeException;

    public abstract void close() throws SQLException;
}
