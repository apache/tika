package org.apache.tika.pipes.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AsyncPipesEmitHook implements AsyncEmitHook {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncPipesEmitHook.class);

    private final PreparedStatement markSuccess;
    private final PreparedStatement markFailure;

    public AsyncPipesEmitHook(Connection connection) throws SQLException  {
        String sql = "delete from parse_queue where id=?";
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
            LOG.warn("problem with on success: "+task.getTaskId(), e);
        }
    }

    @Override
    public void onFail(AsyncTask task) {
        try {
            markFailure.clearParameters();
            markFailure.setLong(1, task.getTaskId());
            markFailure.execute();
        } catch (SQLException e) {
            LOG.warn("problem with on fail: "+task.getTaskId(), e);
        }
    }
}
