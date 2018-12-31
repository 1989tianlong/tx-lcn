package com.codingapi.tx.client.spi.transaction.txc.resource.sql;

import com.codingapi.tx.client.bean.TxTransactionLocal;
import com.codingapi.tx.client.spi.transaction.txc.resource.sql.def.bean.TableStruct;
import org.apache.commons.dbutils.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Description: 数据库表结构分析
 * <p>
 * Date: 2018/12/10
 *
 * @author ujued
 */
@Component
public class TableStructAnalyser {

    @Autowired
    private DataSource dataSource;

    public TableStructAnalyser() {
    }

    public TableStruct analyse(String table) {
        Connection connection = null;
        ResultSet structRs = null;
        ResultSet columnSet = null;
        TableStruct tableStruct = new TableStruct(table);
        try {
            TxTransactionLocal.makeUnProxy();
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            structRs = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, table);
            columnSet = connection.getMetaData().getColumns(null, "%", table, "%");
            while (structRs.next()) {
                tableStruct.getPrimaryKeys().add(structRs.getString("COLUMN_NAME"));
            }
            while (columnSet.next()) {
                tableStruct.getColumns().put(columnSet.getString("COLUMN_NAME"), columnSet.getString("TYPE_NAME"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                DbUtils.close(structRs);
                DbUtils.close(columnSet);
                DbUtils.close(connection);
            } catch (SQLException ignored) {
            }
            TxTransactionLocal.undoProxyStatus();
        }
        return tableStruct;
    }

    /**
     * 存在数据表判断
     *
     * @param tableName
     * @return
     */
    public boolean existsTable(String tableName) {
        Connection connection = null;
        ResultSet resultSet = null;
        try {
            TxTransactionLocal.makeUnProxy();
            connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            resultSet = connection.getMetaData().getTables(null, null, tableName, null);
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                DbUtils.close(resultSet);
                DbUtils.close(connection);
            } catch (SQLException ignored) {
            }
            TxTransactionLocal.undoProxyStatus();
        }
        return false;
    }
}
