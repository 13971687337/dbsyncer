/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.web.controller.index;

import org.dbsyncer.biz.AppConfigService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.parser.model.UserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/index")
public class IndexController {

    @Resource
    private AppConfigService appConfigService;

    @Resource
    private UserConfigService userConfigService;

    @GetMapping("")
    public String index(ModelMap model) {
        return "index/list.html";
    }

    @GetMapping("/version.json")
    @ResponseBody
    public RestResult version() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return RestResult.restSuccess(appConfigService.getVersionInfo(authentication.getName()));
    }

    @GetMapping("/getInfo")
    @ResponseBody
    public RestResult getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserInfo userInfo = userConfigService.getUserInfo(authentication.getName());
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("userName", userInfo.getUsername());
        user.put("nickName", userInfo.getUsername());
        user.put("userId", userInfo.getUsername());
        data.put("user", user);
        data.put("roles", Collections.singletonList(userInfo.getRoleCode()));
        data.put("permissions", Collections.emptyList());
        return RestResult.restSuccess(data);
    }

}