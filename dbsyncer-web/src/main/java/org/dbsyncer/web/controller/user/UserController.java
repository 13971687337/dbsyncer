package org.dbsyncer.web.controller.user;

import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.biz.vo.UserInfoVo;
import org.dbsyncer.web.controller.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户信息管理
 *
 * @author AE86
 * @date 2017年7月7日 上午10:03:33
 */
@Controller
@RequestMapping(value = "/user")
public class UserController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private UserConfigService userConfigService;

    @GetMapping("/getUserInfo.json")
    @ResponseBody
    public RestResult getUserInfo() {
        return RestResult.restSuccess(getUserInfoVo());
    }

    @RequestMapping(value = "/add")
    @ResponseBody
    public RestResult add(HttpServletRequest request) {
        try {
            Map<String, String> params = getParamsWithUserName(request);
            return RestResult.restSuccess(userConfigService.add(params));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @RequestMapping(value = "/edit")
    @ResponseBody
    public RestResult edit(HttpServletRequest request) {
        try {
            Map<String, String> params = getParamsWithUserName(request);
            return RestResult.restSuccess(userConfigService.edit(params));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/search")
    @ResponseBody
    public RestResult search(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);
            String currentUserName = getUserName();
            List<UserInfoVo> all = userConfigService.getUserInfoAll(currentUserName);
            int pageNum = Integer.parseInt(params.getOrDefault("pageNum", "1"));
            int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "50"));
            int start = (pageNum - 1) * pageSize;
            int end = Math.min(start + pageSize, all.size());
            List<UserInfoVo> page = start < all.size() ? all.subList(start, end) : Collections.emptyList();
            Map<String, Object> result = new HashMap<>();
            result.put("data", page);
            result.put("total", all.size());
            return RestResult.restSuccess(result);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/remove")
    @ResponseBody
    public RestResult remove(HttpServletRequest request) {
        try {
            Map<String, String> params = getParamsWithUserName(request);
            return RestResult.restSuccess(userConfigService.remove(params));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    private Map<String, String> getParamsWithUserName(HttpServletRequest request) {
        Map<String, String> params = getParams(request);
        params.put(UserConfigService.CURRENT_USER_NAME, getUserName());
        return params;
    }

    /**
     * 获取登录用户信息
     *
     * @return
     */
    private UserInfoVo getUserInfoVo() {
        String currentUserName = getUserName();
        return userConfigService.getUserInfoVo(currentUserName, currentUserName);
    }

    private String getUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Assert.hasText(username, "无法获取登录用户.");
        return username;
    }


}
