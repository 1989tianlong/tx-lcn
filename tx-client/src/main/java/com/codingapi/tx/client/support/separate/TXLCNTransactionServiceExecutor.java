package com.codingapi.tx.client.support.separate;


import com.codingapi.tx.client.bean.TxTransactionInfo;
import com.codingapi.tx.client.support.LCNTransactionBeanHelper;
import com.codingapi.tx.client.support.common.TransactionUnitTypeList;
import com.codingapi.tx.client.support.common.cache.TransactionAttachmentCache;
import com.codingapi.tx.commons.exception.BeforeBusinessException;
import com.codingapi.tx.logger.TxLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * LCN分布式事务业务执行器
 * Created by lorne on 2017/6/8.
 */
@Component
@Slf4j
public class TXLCNTransactionServiceExecutor {


    @Autowired
    private LCNTransactionBeanHelper lcnTransactionBeanHelper;

    @Autowired
    private TransactionAttachmentCache transactionAttachmentCache;

    @Autowired
    private TxLogger txLogger;

    /**
     * 事务业务执行
     *
     * @param info
     * @return
     * @throws Throwable
     */
    public Object transactionRunning(TxTransactionInfo info) throws Throwable {

        // 1. 获取事务类型
        String transactionType = info.getTransactionType();

        // 2. 事务状态抉择器
        TXLCNTransactionSeparator lcnTransactionSeparator =
                lcnTransactionBeanHelper.loadLCNTransactionStateResolver(info.getTransactionType());

        // 3. 获取事务状态
        TXLCNTransactionState lcnTransactionState = lcnTransactionSeparator.loadTransactionState(info);

        // 4. 获取bean
        TXLCNTransactionControl lcnTransactionControl =
                lcnTransactionBeanHelper.loadLCNTransactionControl(transactionType, lcnTransactionState);

        // 5. 织入事务操作

        // 5.1 记录事务类型到事务上下文
        transactionAttachmentCache.attach(
                info.getGroupId(), info.getUnitId(), new TransactionUnitTypeList().selfAdd(transactionType));

        try {
            // 5.2 业务执行前
            txLogger.trace(info.getGroupId(), info.getUnitId(), "transaction", "pre business code");
            lcnTransactionControl.preBusinessCode(info);
            // 5.3 执行业务
            txLogger.trace(info.getGroupId(), info.getUnitId(), "transaction", "do business code");
            Object result = lcnTransactionControl.doBusinessCode(info);

            // 5.4 业务执行成功
            txLogger.trace(info.getGroupId(), info.getUnitId(), "transaction", "business code success");
            lcnTransactionControl.onBusinessCodeSuccess(info, result);
            return result;
        }catch (BeforeBusinessException e){
            log.error("business",e);
            throw e;
        } catch (Throwable e) {
            // 5.5 业务执行失败
            txLogger.trace(info.getGroupId(), info.getUnitId(), "transaction", "business code error");
            lcnTransactionControl.onBusinessCodeError(info, e);
            throw e;
        } finally {
            // 5.6 业务执行完毕
            txLogger.trace(info.getGroupId(), info.getUnitId(), "transaction", "finally business code");
            lcnTransactionControl.postBusinessCode(info);
        }
    }


}
