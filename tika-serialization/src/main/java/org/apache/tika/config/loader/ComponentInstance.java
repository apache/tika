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
package org.apache.tika.config.loader;

/**
 * Holds a component instance along with its metadata (priority, etc.).
 *
 * @param <T> the component type
 */
public class ComponentInstance<T> implements Comparable<ComponentInstance<T>> {

    private final String name;
    private final T instance;
    private final int priority;

    public ComponentInstance(String name, T instance, int priority) {
        this.name = name;
        this.instance = instance;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public T getInstance() {
        return instance;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(ComponentInstance<T> other) {
        // Lower priority value = higher priority (processed first)
        int priorityCompare = Integer.compare(this.priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // Secondary sort by name for stability
        return this.name.compareTo(other.name);
    }

    @Override
    public String toString() {
        return "ComponentInstance{" +
                "name='" + name + '\'' +
                ", instance=" + instance.getClass().getSimpleName() +
                ", priority=" + priority +
                '}';
    }
}
