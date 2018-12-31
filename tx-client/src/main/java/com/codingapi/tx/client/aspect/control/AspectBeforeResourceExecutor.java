package com.codingapi.tx.client.aspect.control;

import com.codingapi.tx.client.bean.TxTransactionLocal;
import com.codingapi.tx.client.support.LCNTransactionBeanHelper;
import com.codingapi.tx.client.support.resouce.TransactionResourceExecutor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Objects;

/**
 * @author lorne
 * @date 2018/12/2
 * @description
 */
@Component
@Slf4j
public class AspectBeforeResourceExecutor {


    @Autowired
    private LCNTransactionBeanHelper transactionBeanHelper;

    public Object around(ProceedingJoinPoint point) throws Throwable {
        TxTransactionLocal txTransactionLocal = TxTransactionLocal.current();
        if (Objects.nonNull(txTransactionLocal) && txTransactionLocal.isProxy()) {
            String transactionType = txTransactionLocal.getTransactionType();
            TransactionResourceExecutor transactionResourceExecutor = transactionBeanHelper.loadTransactionResourceExecuter(transactionType);
            Connection connection = transactionResourceExecutor.proxyConnection(() -> {
                try {
                    return (Connection) point.proceed();
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            });
            log.info("proxy a sql connection: {}.", connection);
            return connection;
        }
        return point.proceed();
    }
}
