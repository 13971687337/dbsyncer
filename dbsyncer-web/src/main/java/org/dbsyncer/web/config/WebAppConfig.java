package org.dbsyncer.web.config;

import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.SHA1Util;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@Configuration
@EnableWebSecurity
@ConfigurationProperties(prefix = "dbsyncer.web.security")
public class WebAppConfig {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String LOGIN = "/login";
    private static final String LOGIN_PAGE = "/login.html";
    private static final int MAXIMUM_SESSIONS = 1;

    @Resource
    private UserConfigService userConfigService;

    private boolean resetPwd;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/plugins/**", "/index/version.json").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl(LOGIN)
                .loginPage(LOGIN_PAGE)
                .successHandler(loginSuccessHandler())
                .failureHandler(loginFailHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .permitAll()
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(logoutHandler())
            )
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
                .maximumSessions(MAXIMUM_SESSIONS)
            );
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new DBSyncerAuthenticationProvider();
    }

    @Bean
    public HttpSessionListener httpSessionListener() {
        return new HttpSessionListener() {
            @Override
            public void sessionCreated(HttpSessionEvent se) {
                logger.debug("创建会话:{}", se.getSession().getId());
            }
            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                logger.debug("销毁会话:{}", se.getSession().getId());
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler loginFailHandler() {
        return (request, response, e) -> write(response, RestResult.restFail(e.getMessage(), 401));
    }

    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler loginSuccessHandler() {
        return new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
                String msg = String.format("%s 登录成功!", authentication.getPrincipal());
                write(response, RestResult.restSuccess(msg));
                logger.info(msg);
            }
        };
    }

    @Bean
    public LogoutSuccessHandler logoutHandler() {
        return (request, response, authentication) -> {
            try {
                String msg = String.format("%s 注销成功!", authentication.getPrincipal());
                write(response, RestResult.restSuccess(msg));
                logger.info(msg);
            } catch (Exception e) {
                write(response, RestResult.restFail(e.getMessage(), 403));
                logger.info("注销失败: {}", e.getMessage());
            }
        };
    }

    private class DBSyncerAuthenticationProvider implements AuthenticationProvider {
        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            String username = (String) authentication.getPrincipal();
            String password = (String) authentication.getCredentials();
            password = SHA1Util.b64_sha1(password);

            UserInfo userInfo = userConfigService.getUserInfo(username);
            if (resetPwd && userInfo != null) {
                UserInfo defUser = userConfigService.getDefaultUser();
                if (StringUtil.equals(defUser.getUsername(), username) && StringUtil.equals(defUser.getPassword(), password)) {
                    userInfo.setPassword(password);
                    logger.info("重置[{}]密码成功!", username);
                }
            }
            if (null == userInfo || !StringUtil.equals(userInfo.getPassword(), password)) {
                throw new BadCredentialsException("对不起,您输入的帐号或密码错误");
            }
            List<GrantedAuthority> authorities = AuthorityUtils.commaSeparatedStringToAuthorityList(userInfo.getRoleCode());
            return new UsernamePasswordAuthenticationToken(username, password, authorities);
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return true;
        }
    }

    private void write(HttpServletResponse response, RestResult result) {
        PrintWriter out = null;
        try {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(result.getStatus());
            out = response.getWriter();
            out.write(JsonUtil.objToJson(result));
            out.flush();
        } catch (IOException e) {
            logger.error(e.getMessage());
        } finally {
            if (null != out) {
                out.close();
            }
        }
    }

    public void setResetPwd(boolean resetPwd) {
        this.resetPwd = resetPwd;
    }
}
