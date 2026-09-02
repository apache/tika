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
package org.apache.tika.server.core.resource;

/**
 * In-process carrier for a request's selected preset name, riding the request
 * ParseContext between the resource and {@link PipesParsingHelper}, which lifts it
 * onto the tuple's own preset field before serialization. Never travels on the wire
 * itself (the wire serializer refuses unregistered context entries, so a leak fails
 * loudly). The preset's content is resolved by the forked worker from its own config
 * at config-tier trust.
 */
public record PresetSelection(String name) {
}
