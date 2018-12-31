package com.codingapi.tx.client.support.separate;

import com.codingapi.tx.client.bean.TxTransactionInfo;

/**
 * Description: 事务分离器
 * Date: 2018/12/5
 *
 * @author ujued
 */
public interface TXLCNTransactionSeparator {

    /**
     * 判断事务状态
     *
     * @param txTransactionInfo
     * @return
     */
    TXLCNTransactionState loadTransactionState(TxTransactionInfo txTransactionInfo);
}
