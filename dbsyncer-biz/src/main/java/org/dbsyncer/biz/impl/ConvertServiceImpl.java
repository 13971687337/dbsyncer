/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.ConvertService;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * @author zhangxl
 * @version 1.0.0
 * @date 2020/1/14 0:03
 */
@Component
public class ConvertServiceImpl implements ConvertService {

    @Resource
    private ProfileComponent profileComponent;

    @Override
    public List<ConvertEnum> getConvertEnumAll() {
        return profileComponent.getConvertEnumAll();
    }

}