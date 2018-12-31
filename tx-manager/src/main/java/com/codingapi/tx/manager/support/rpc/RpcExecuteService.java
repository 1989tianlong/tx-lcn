package com.codingapi.tx.manager.support.rpc;

import com.codingapi.tx.commons.exception.TxManagerException;
import com.codingapi.tx.manager.support.TransactionCmd;

/**
 * @author lorne
 * @date 2018/12/2
 * @description LCN分布式事务 manager业务处理
 */
public interface RpcExecuteService {

    /**
     * 执行业务
     *
     * @return
     */
    Object execute(TransactionCmd transactionCmd) throws TxManagerException;

}
