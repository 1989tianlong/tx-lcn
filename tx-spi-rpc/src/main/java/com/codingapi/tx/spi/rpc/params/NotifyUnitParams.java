package com.codingapi.tx.spi.rpc.params;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description:
 * Date: 2018/12/5
 *
 * @author ujued
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class NotifyUnitParams {
    private String groupId;

    private String unitId;

    private String unitType;

    private int state;
}
