package com.codingapi.tx.client.support.rpc;

import com.codingapi.tx.commons.rpc.params.TxExceptionParams;
import com.codingapi.tx.spi.rpc.RpcClient;
import com.codingapi.tx.spi.rpc.exception.RpcException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Description:
 * Date: 2018/12/29
 *
 * @author ujued
 */
@Component
@Slf4j
public class TxMangerReporter {

    private final RpcClient rpcClient;

    @Autowired
    public TxMangerReporter(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    /**
     * Manager 记录事务状态
     *
     * @param groupId
     * @param unitId
     * @param registrar
     * @param state
     */
    public void reportTransactionState(String groupId, String unitId, Short registrar, int state) {
        TxExceptionParams txExceptionParams = new TxExceptionParams();
        txExceptionParams.setGroupId(groupId);
        txExceptionParams.setRegistrar(registrar);
        txExceptionParams.setTransactionState((short) state);
        txExceptionParams.setUnitId(unitId);
        while (true) {
            try {
                rpcClient.send(rpcClient.loadRemoteKey(), MessageCreator.writeTxException(txExceptionParams));
                break;
            } catch (RpcException e) {
                if (e.getCode() == RpcException.NON_TX_MANAGER) {
                    log.error("report transaction state error. non tx-manager is alive.");
                    break;
                }
            }
        }
    }
}
