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
package org.apache.tika.pipes.async;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPipesEmitHook implements AsyncEmitHook {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncPipesEmitHook.class);

    private final PreparedStatement markSuccess;
    private final PreparedStatement markFailure;

    public AsyncPipesEmitHook(Connection connection) throws SQLException {
        String sql = "delete from task_queue where id=?";
        markSuccess = connection.prepareStatement(sql);
        //TODO --fix this
        markFailure = connection.prepareStatement(sql);
    }

    @Override
    public void onSuccess(AsyncTask task) {
        try {
            markSuccess.clearParameters();
            markSuccess.setLong(1, task.getTaskId());
            markSuccess.execute();
        } catch (SQLException e) {
            LOG.warn("problem with on success: " + task.getTaskId(), e);
        }
    }

    @Override
    public void onFail(AsyncTask task) {
        try {
            markFailure.clearParameters();
            markFailure.setLong(1, task.getTaskId());
            markFailure.execute();
        } catch (SQLException e) {
            LOG.warn("problem with on fail: " + task.getTaskId(), e);
        }
    }
}
