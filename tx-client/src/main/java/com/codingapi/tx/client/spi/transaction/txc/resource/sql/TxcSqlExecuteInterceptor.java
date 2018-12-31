package com.codingapi.tx.client.spi.transaction.txc.resource.sql;

import com.codingapi.tx.client.bean.TxTransactionLocal;
import com.codingapi.tx.client.spi.transaction.txc.resource.sql.def.SqlExecuteInterceptor;
import com.codingapi.tx.client.spi.transaction.txc.resource.sql.def.TxcService;
import com.codingapi.tx.client.spi.transaction.txc.resource.sql.def.bean.*;
import com.codingapi.tx.client.spi.transaction.txc.resource.sql.util.SqlUtils;
import com.codingapi.tx.jdbcproxy.p6spy.common.StatementInformation;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.commons.dbutils.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Description: 拦截必要的SQL, 植入TXC逻辑
 * <p>
 * Date: 2018/12/13
 *
 * @author ujued
 */
@Component
@Slf4j
public class TxcSqlExecuteInterceptor implements SqlExecuteInterceptor {

    private final TableStructAnalyser tableStructAnalyser;

    private final TxcService txcService;

    @Autowired
    public TxcSqlExecuteInterceptor(TableStructAnalyser tableStructAnalyser, TxcService txcService) {
        this.tableStructAnalyser = tableStructAnalyser;
        this.txcService = txcService;
    }

    @Override
    public void preUpdate(Update update) throws SQLException {

        // 获取线程传递参数
        String groupId = TxTransactionLocal.current().getGroupId();
        String unitId = TxTransactionLocal.current().getUnitId();
        RollbackInfo rollbackInfo = (RollbackInfo) TxTransactionLocal.current().getAttachment();


        // Update相关数据准备
        List<String> columns = new ArrayList<>(update.getColumns().size());
        List<String> primaryKeys = new ArrayList<>(3);
        List<String> tables = new ArrayList<>(update.getTables().size());
        update.getColumns().forEach(column -> {
            // TODO 记录Update 语句限制 当更新的字段名没有指定表名时，用第一张表
            column.setTable(update.getTables().get(0));
            columns.add(column.getFullyQualifiedName());
        });
        for (Table table : update.getTables()) {
            tables.add(table.getName());
            TableStruct tableStruct = tableStructAnalyser.analyse(table.getName());
            tableStruct.getPrimaryKeys().forEach(key -> primaryKeys.add(table.getName() + "." + key));
        }

        // 前置准备
        txcService.resolveUpdateImage(new UpdateImageParams()
                .setGroupId(groupId)
                .setUnitId(unitId)
                .setRollbackInfo(rollbackInfo)
                .setColumns(columns)
                .setPrimaryKeys(primaryKeys)
                .setTables(tables)
                .setWhereSql(update.getWhere() == null ? "1=1" : update.getWhere().toString()));
    }

    @Override
    public void preDelete(Delete delete) throws SQLException {
        log.info("do pre delete");

        // 获取线程传递参数
        RollbackInfo rollbackInfo = (RollbackInfo) TxTransactionLocal.current().getAttachment();
        String groupId = TxTransactionLocal.current().getGroupId();
        String unitId = TxTransactionLocal.current().getUnitId();

        // Delete Sql 数据
        List<String> tables = new ArrayList<>(delete.getTables().size());
        List<String> primaryKeys = new ArrayList<>(3);
        List<String> columns = new ArrayList<>();

        delete.getTables().forEach(table -> {
            TableStruct tableStruct = tableStructAnalyser.analyse(table.getName());
            tableStruct.getColumns().forEach((k, v) -> {
                columns.add(tableStruct.getTableName() + SqlUtils.DOT + k);
            });
            tableStruct.getPrimaryKeys().forEach(primaryKey -> {
                primaryKeys.add(tableStruct.getTableName() + SqlUtils.DOT + primaryKey);
            });
            tables.add(tableStruct.getTableName());
        });

        // 前置准备
        txcService.resolveDeleteImage(new DeleteImageParams()
                .setGroupId(groupId)
                .setUnitId(unitId)
                .setRollbackInfo(rollbackInfo)
                .setSqlWhere(delete.getWhere().toString())
                .setColumns(columns)
                .setPrimaryKeys(primaryKeys)
                .setTables(tables));

    }

    @Override
    public void preInsert(Insert insert) {
    }

    @Override
    public void postInsert(StatementInformation statementInformation) throws SQLException {
        Insert insert = (Insert) statementInformation.getAttachment();
        TableStruct tableStruct = tableStructAnalyser.analyse(insert.getTable().getName());

        // 解决主键
        PrimaryKeyListVisitor primaryKeyListVisitor = new PrimaryKeyListVisitor(insert.getTable(),
                insert.getColumns(), tableStruct.getFullyQualifiedPrimaryKeys());
        insert.getItemsList().accept(primaryKeyListVisitor);

        // 自增主键
        ResultSet rs = statementInformation.getStatement().getGeneratedKeys();
        StringBuilder rollbackSql = new StringBuilder(SqlUtils.DELETE)
                .append(SqlUtils.FROM)
                .append(tableStruct.getTableName())
                .append(SqlUtils.WHERE);
        List<Object> params = new ArrayList<>();

        for (int i = 0; rs.next(); i++) {
            Map<String, Object> pks = primaryKeyListVisitor.getPrimaryKeyValuesList().get(i);
            for (String key : tableStruct.getFullyQualifiedPrimaryKeys()) {
                rollbackSql.append(key).append("=? and ");
                if (pks.containsKey(key)) {
                    params.add(pks.get(key));
                } else {
                    params.add(rs.getObject(1));
                }
            }
            SqlUtils.cutSuffix(SqlUtils.AND, rollbackSql);
            rollbackSql.append(SqlUtils.OR);
        }
        DbUtils.close(rs);
        SqlUtils.cutSuffix(SqlUtils.OR, rollbackSql);

        if (params.size() == 0) {
            log.warn("nothing to insert");
            return;
        }

        // 设置Rollback SQL
        RollbackInfo rollbackInfo = (RollbackInfo) TxTransactionLocal.current().getAttachment();
        Object[] paramArray = new Object[params.size()];
        params.toArray(paramArray);
        rollbackInfo.getRollbackSqlList().add(new StatementInfo(rollbackSql.toString(), paramArray));
    }

    @Override
    public void preSelect(LockableSelect lockableSelect) throws SQLException {
        // 忽略无锁的查询
        if (!lockableSelect.shouldLock()) {
            return;
        }

        // 不支持非PlainSelect
        if (!(lockableSelect.statement().getSelectBody() instanceof PlainSelect)) {
            throw new SQLException("non support this query when use control lock.");
        }

        PlainSelect plainSelect = (PlainSelect) lockableSelect.statement().getSelectBody();

        // 不支持复杂的FromItem
        if (!(plainSelect.getFromItem() instanceof Table)) {
            throw new SQLException("non support this query when use control lock.");
        }


        // 构造查询需要判断锁行的SQL
        List<String> primaryKeys = new ArrayList<>();
        Table leftTable = (Table) plainSelect.getFromItem();
        List<SelectItem> selectItems = new ArrayList<>();

        TableStruct leftTableStruct = tableStructAnalyser.analyse(leftTable.getName());
        leftTableStruct.getPrimaryKeys().forEach(primaryKey -> {
            Column column = new Column(leftTable, primaryKey);
            selectItems.add(new SelectExpressionItem(column));
            primaryKeys.add(column.getFullyQualifiedName());
        });

        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                if (join.isSimple()) {
                    TableStruct rightTableStruct = tableStructAnalyser.analyse(join.getRightItem().toString());
                    rightTableStruct.getPrimaryKeys().forEach(primaryKey -> {
                        Column column = new Column((Table) join.getRightItem(), primaryKey);
                        selectItems.add(new SelectExpressionItem(column));
                        primaryKeys.add(column.getFullyQualifiedName());
                    });
                }
            }
        }
        plainSelect.setSelectItems(selectItems);

        // 尝试锁定
        log.info("lock select sql: {}", plainSelect);
        String groupId = TxTransactionLocal.current().getGroupId();
        String unitId = TxTransactionLocal.current().getUnitId();
        RollbackInfo rollbackInfo = (RollbackInfo) TxTransactionLocal.current().getAttachment();

        SelectImageParams selectImageParams = new SelectImageParams();
        selectImageParams.setGroupId(groupId);
        selectImageParams.setUnitId(unitId);
        selectImageParams.setPrimaryKeys(primaryKeys);
        selectImageParams.setRollbackInfo(rollbackInfo);
        selectImageParams.setSql(plainSelect.toString());

        txcService.lockSelect(selectImageParams, lockableSelect.isxLock());
    }

}
