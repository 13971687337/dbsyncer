# 前端项目结构重构 + JWT 认证改造 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 dbsyncer-web-ui 前端代码重构为 RuoYi 风格（request 封装、API 模块化、路由守卫），同时将后端认证从 Session 改为 JWT。

**Architecture:** 前端新增 `utils/request.ts`（axios 拦截器链 + /dev-api 前缀）、`utils/auth.ts`（js-cookie token 管理）、`settings.ts`（RuoYi-Vue3 全局配置）、`permission.ts`（路由守卫 + 动态标题），`vite/plugins/`（auto-import/compression/setup-extend），`api/` 目录按业务拆分；后端新增 JWT 工具类、JWT 认证过滤器、`/getInfo` 接口。渐进式推进：基础设施 → vite 配置 → 后端 JWT → 认证流程 → 业务 API → 清理。

**Tech Stack:** Vue 3 + TypeScript + Pinia + Element Plus + Axios + Vite；Spring Boot + Spring Security + JWT (jjwt)
**Reference:** RuoYi-Vue3（`/Users/work2021/DataCenterRespo/opensource2026/RuoYi-Vue3`）

---

### Task 1: 安装前端依赖

**Files:**
- Modify: `dbsyncer-web-ui/package.json`

- [ ] **Step 1: 安装所有前端依赖**

```bash
cd /Users/work2021/DataCenterRespo/hc-dbsync/dbsyncer-web-ui
npm install js-cookie nprogress
npm install -D @types/js-cookie @types/nprogress
npm install -D unplugin-auto-import unplugin-vue-setup-extend-plus
npm install -D vite-plugin-compression
```

- [ ] **Step 2: 更新 package.json scripts（参考 RuoYi-Vue3）**

修改 `"scripts"` 字段：

```json
"scripts": {
  "dev": "vite",
  "build:prod": "vue-tsc --noEmit && vite build",
  "build:stage": "vue-tsc --noEmit && vite build --mode staging",
  "preview": "vite preview"
}
```

变更：
- 旧 `"build"` → `"build:prod"`（生产构建，保留 vue-tsc 类型检查）
- 新增 `"build:stage"` → `vite build --mode staging`（测试环境构建）
- `vue-tsc --noEmit` 替代 `vue-tsc`（只检查类型不输出，更快）

- [ ] **Step 3: 验证安装**

```bash
node -e "require('js-cookie'); require('nprogress'); console.log('OK')"
```
Expected: OK

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-web-ui/package.json dbsyncer-web-ui/package-lock.json
git commit -m "chore: add dependencies and update scripts — RuoYi-Vue3 pattern

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 2: 创建前端基础设施 — utils/auth.ts

**Files:**
- Create: `dbsyncer-web-ui/src/utils/auth.ts`

- [ ] **Step 1: 创建 auth.ts**

```typescript
import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'

export function getToken(): string | undefined {
  return Cookies.get(TokenKey)
}

export function setToken(token: string): void {
  Cookies.set(TokenKey, token)
}

export function removeToken(): void {
  Cookies.remove(TokenKey)
}
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/utils/auth.ts
git commit -m "feat: add utils/auth.ts — JWT token storage via js-cookie

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: 创建前端基础设施 — utils/errorCode.ts

**Files:**
- Create: `dbsyncer-web-ui/src/utils/errorCode.ts`

- [ ] **Step 1: 创建 errorCode.ts**

```typescript
const errorCode: Record<string | number, string> = {
  '401': '登录状态已过期，请重新登录',
  '403': '没有操作权限',
  '500': '服务器内部错误',
  'default': '系统未知错误，请反馈给管理员',
}

export default errorCode
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/utils/errorCode.ts
git commit -m "feat: add utils/errorCode.ts — HTTP error code mappings

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: 创建前端基础设施 — utils/request.ts

**Files:**
- Create: `dbsyncer-web-ui/src/utils/request.ts`

- [ ] **Step 1: 创建 request.ts**

```typescript
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'

export const isRelogin = { show: false }

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API as string,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
})

// 请求拦截器：自动带 JWT token
service.interceptors.request.use(config => {
  const isToken = (config.headers as any)?.isToken === false
  const token = getToken()
  if (token && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, error => Promise.reject(error))

// 响应拦截器：统一错误处理
service.interceptors.response.use(res => {
  const body = res.data
  const status = body.status || 200
  const msg = errorCode[status] || body.message || errorCode['default']

  // 二进制数据直接返回
  if (res.request.responseType === 'blob' || res.request.responseType === 'arraybuffer') {
    return body
  }

  if (status === 401) {
    if (!isRelogin.show) {
      isRelogin.show = true
      ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => {
        isRelogin.show = false
        removeToken()
        location.href = '/login'
      }).catch(() => {
        isRelogin.show = false
      })
    }
    return Promise.reject(new Error('无效的会话，或者会话已过期，请重新登录。'))
  } else if (status === 500) {
    ElMessage({ message: msg, type: 'error' })
    return Promise.reject(new Error(msg))
  } else if (!body.success) {
    ElMessage({ message: msg, type: 'warning' })
    return Promise.reject(msg as string)
  }
  return body
}, error => {
  let message = error.message
  if (message === 'Network Error') {
    message = '后端接口连接异常'
  } else if (message.includes('timeout')) {
    message = '系统接口请求超时'
  }
  ElMessage({ message, type: 'error', duration: 5 * 1000 })
  return Promise.reject(error)
})

export default service
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/utils/request.ts
git commit -m "feat: add utils/request.ts — axios instance with JWT interceptor

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: 创建环境变量文件（参考 RuoYi-Vue3 三文件模式）

**Files:**
- Create: `dbsyncer-web-ui/.env.development`
- Create: `dbsyncer-web-ui/.env.staging`
- Create: `dbsyncer-web-ui/.env.production`
- Modify: `dbsyncer-web-ui/src/vite-env.d.ts`

- [ ] **Step 1: 创建 .env.development**

```
# 页面标题
VITE_APP_TITLE = 'HC-DBSync'

# 开发环境
VITE_APP_ENV = 'development'

# 开发环境接口前缀（Vite proxy 会 rewrite 到后端实际地址）
VITE_APP_BASE_API = '/dev-api'
```

- [ ] **Step 2: 创建 .env.staging**

```
# 页面标题
VITE_APP_TITLE = 'HC-DBSync'

# 测试环境
VITE_APP_ENV = 'staging'

# 测试环境接口前缀
VITE_APP_BASE_API = '/stage-api'

# 是否开启 gzip/brotli 压缩
VITE_BUILD_COMPRESS = 'gzip'
```

- [ ] **Step 3: 创建 .env.production**

```
# 页面标题
VITE_APP_TITLE = 'HC-DBSync'

# 生产环境
VITE_APP_ENV = 'production'

# 生产环境接口前缀
VITE_APP_BASE_API = '/prod-api'

# 是否开启 gzip/brotli 压缩
VITE_BUILD_COMPRESS = 'gzip'
```

- [ ] **Step 4: 更新 vite-env.d.ts 添加类型声明**

```typescript
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_ENV: string
  readonly VITE_APP_BASE_API: string
  readonly VITE_BUILD_COMPRESS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

- [ ] **Step 5: Commit**

```bash
git add dbsyncer-web-ui/.env.development dbsyncer-web-ui/.env.staging dbsyncer-web-ui/.env.production dbsyncer-web-ui/src/vite-env.d.ts
git commit -m "feat: add env files — dev/staging/prod with RuoYi-Vue3 pattern

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: 创建 src/settings.ts — 全局配置（参考 RuoYi-Vue3）

**Files:**
- Create: `dbsyncer-web-ui/src/settings.ts`

- [ ] **Step 1: 创建 settings.ts**

```typescript
const settings: Settings = {
  title: import.meta.env.VITE_APP_TITLE,
  sideTheme: 'theme-dark',
  showSettings: false,
  navType: 1,
  tagsView: false,
  fixedHeader: true,
  sidebarLogo: true,
  dynamicTitle: false,
  footerVisible: false,
  footerContent: 'Copyright © 2024-2026 武汉互创联合科技. All Rights Reserved.',
}

export interface Settings {
  /** 网页标题 */
  title: string
  /** 侧边栏主题 theme-dark / theme-light */
  sideTheme: string
  /** 是否显示右侧设置面板 */
  showSettings: boolean
  /** 菜单导航模式 1=纯左侧 2=混合 3=纯顶部 */
  navType: number
  /** 是否显示 tagsView 标签页 */
  tagsView: boolean
  /** 是否固定头部 */
  fixedHeader: boolean
  /** 是否显示侧边栏 Logo */
  sidebarLogo: boolean
  /** 是否显示动态标题 */
  dynamicTitle: boolean
  /** 是否显示底部版权 */
  footerVisible: boolean
  /** 底部版权文本 */
  footerContent: string
}

export default settings
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/settings.ts
git commit -m "feat: add settings.ts — global app configuration (RuoYi-Vue3 pattern)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: 创建 utils/dynamicTitle.ts — 动态标题

**Files:**
- Create: `dbsyncer-web-ui/src/utils/dynamicTitle.ts`

- [ ] **Step 1: 创建 dynamicTitle.ts**

```typescript
import defaultSettings from '@/settings'

export function useDynamicTitle(): void {
  document.title = defaultSettings.title
}
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/utils/dynamicTitle.ts
git commit -m "feat: add utils/dynamicTitle.ts — page title management

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 8: 创建 vite/plugins/ — 复用 RuoYi-Vue3 插件

**Files:**
- Create: `dbsyncer-web-ui/vite/plugins/index.ts`
- Create: `dbsyncer-web-ui/vite/plugins/auto-import.ts`
- Create: `dbsyncer-web-ui/vite/plugins/compression.ts`
- Create: `dbsyncer-web-ui/vite/plugins/setup-extend.ts`

- [ ] **Step 1: 创建 vite/plugins/index.ts**

```typescript
import vue from '@vitejs/plugin-vue'
import createAutoImport from './auto-import'
import createCompression from './compression'
import createSetupExtend from './setup-extend'

export default function createVitePlugins(viteEnv: Record<string, string>, isBuild: boolean) {
  const vitePlugins = [vue()]
  vitePlugins.push(createAutoImport())
  vitePlugins.push(createSetupExtend())
  if (isBuild) {
    vitePlugins.push(...createCompression(viteEnv))
  }
  return vitePlugins
}
```

- [ ] **Step 2: 创建 vite/plugins/auto-import.ts**

```typescript
import autoImport from 'unplugin-auto-import/vite'

export default function createAutoImport() {
  return autoImport({
    imports: [
      'vue',
      'vue-router',
      'pinia',
    ],
    dts: false,
  })
}
```

- [ ] **Step 3: 创建 vite/plugins/compression.ts**

```typescript
import compression from 'vite-plugin-compression'
import type { Plugin } from 'vite'

export default function createCompression(env: Record<string, string>): Plugin[] {
  const { VITE_BUILD_COMPRESS } = env
  const plugins: Plugin[] = []
  if (VITE_BUILD_COMPRESS) {
    const compressList = VITE_BUILD_COMPRESS.split(',')
    if (compressList.includes('gzip')) {
      plugins.push(compression({ ext: '.gz', deleteOriginFile: false }) as Plugin)
    }
    if (compressList.includes('brotli')) {
      plugins.push(compression({ ext: '.br', algorithm: 'brotliCompress', deleteOriginFile: false }) as Plugin)
    }
  }
  return plugins
}
```

- [ ] **Step 4: 创建 vite/plugins/setup-extend.ts**

```typescript
import setupExtend from 'unplugin-vue-setup-extend-plus/vite'

export default function createSetupExtend() {
  return setupExtend({})
}
```

- [ ] **Step 5: Commit**

```bash
git add dbsyncer-web-ui/vite/plugins/
git commit -m "feat: add vite/plugins — auto-import, compression, setup-extend (RuoYi-Vue3)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 9: 后端 — 添加 JWT 依赖 (原 Task 6)

**Files:**
- Modify: `dbsyncer-web/pom.xml`

- [ ] **Step 1: 检查父 POM 是否已有 jjwt**

```bash
grep -r "jjwt\|java-jwt" /Users/work2021/DataCenterRespo/hc-dbsync/pom.xml /Users/work2021/DataCenterRespo/hc-dbsync/dbsyncer-web/pom.xml 2>/dev/null
```

- [ ] **Step 2: 在 dbsyncer-web/pom.xml 中添加 jjwt 依赖**

Read `dbsyncer-web/pom.xml` first. Add inside `<dependencies>`:

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 3: Commit**

```bash
git add dbsyncer-web/pom.xml
git commit -m "feat: add jjwt 0.12.6 dependency for JWT auth

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 10: 后端 — 创建 JWT 工具类

**Files:**
- Create: `dbsyncer-web/src/main/java/org/dbsyncer/web/security/JwtTokenUtil.java`

- [ ] **Step 1: 创建 JwtTokenUtil.java**

```java
package org.dbsyncer.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public final class JwtTokenUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenUtil.class);

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
        "HC-DBSync-JWT-Secret-Key-2026-Must-Be-At-Least-256-Bits!!".getBytes(StandardCharsets.UTF_8)
    );

    private static final long EXPIRATION_MS = 30 * 60 * 1000L; // 30 分钟

    private JwtTokenUtil() {}

    public static String createToken(String username, Map<String, Object> claims) {
        Date now = new Date();
        return Jwts.builder()
            .subject(username)
            .claims(claims)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + EXPIRATION_MS))
            .signWith(SECRET_KEY)
            .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(SECRET_KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public static String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public static boolean isExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            logger.debug("Token 验证失败: {}", e.getMessage());
            return true;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web/src/main/java/org/dbsyncer/web/security/JwtTokenUtil.java
git commit -m "feat: add JwtTokenUtil — JWT token create/parse/validate

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 11: 后端 — 创建 JWT 认证过滤器

**Files:**
- Create: `dbsyncer-web/src/main/java/org/dbsyncer/web/security/JwtAuthenticationFilter.java`

- [ ] **Step 1: 创建 JwtAuthenticationFilter.java**

```java
package org.dbsyncer.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dbsyncer.parser.model.UserInfo;
import org.dbsyncer.biz.UserConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final UserConfigService userConfigService;

    public JwtAuthenticationFilter(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                if (!JwtTokenUtil.isExpired(token)) {
                    String username = JwtTokenUtil.getUsername(token);
                    UserInfo userInfo = userConfigService.getUserInfo(username);
                    if (userInfo != null) {
                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                username, null,
                                AuthorityUtils.commaSeparatedStringToAuthorityList(userInfo.getRoleCode())
                            );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                logger.debug("JWT 认证失败: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web/src/main/java/org/dbsyncer/web/security/JwtAuthenticationFilter.java
git commit -m "feat: add JwtAuthenticationFilter — once-per-request JWT validation

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 12: 后端 — 修改 WebAppConfig 支持 JWT

**Files:**
- Modify: `dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java`

- [ ] **Step 1: 读取当前 WebAppConfig.java**

Already read — it's in `dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java`.

- [ ] **Step 2: 修改 WebAppConfig — 添加 JWT 过滤器，改造登录成功 handler**

Replace the `securityFilterChain` method and add the JWT filter bean:

```java
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
        )
        .addFilterBefore(jwtAuthenticationFilter(),
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
    return http.build();
}

@Bean
public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter(userConfigService);
}
```

- [ ] **Step 3: 修改登录成功 handler — 返回 JWT token**

Replace the `loginSuccessHandler` method:

```java
@Bean
public SavedRequestAwareAuthenticationSuccessHandler loginSuccessHandler() {
    return new SavedRequestAwareAuthenticationSuccessHandler() {
        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                            Authentication authentication) {
            String username = (String) authentication.getPrincipal();
            String token = JwtTokenUtil.createToken(username, Map.of());
            String msg = String.format("%s 登录成功!", username);
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("msg", msg);
            write(response, RestResult.restSuccess(data));
            logger.info(msg);
        }
    };
}
```

Add import at top:
```java
import java.util.Map;
import java.util.HashMap;
import org.dbsyncer.web.security.JwtAuthenticationFilter;
import org.dbsyncer.web.security.JwtTokenUtil;
```

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java
git commit -m "feat: integrate JWT filter into Spring Security config

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 13: 后端 — 创建 /getInfo 接口

**Files:**
- Create/Modify: `dbsyncer-web/src/main/java/org/dbsyncer/web/controller/index/IndexController.java`

- [ ] **Step 1: 读取 IndexController.java**

```bash
Read dbsyncer-web/src/main/java/org/dbsyncer/web/controller/index/IndexController.java
```

- [ ] **Step 2: 在 IndexController 中添加 /getInfo 端点和 UserConfigService 注入**

Add `@Resource` for `UserConfigService` and the new method:

```java
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.parser.model.UserInfo;

// add field:
@Resource
private UserConfigService userConfigService;

// add method:
@GetMapping("/getInfo")
@ResponseBody
public RestResult getUserInfo() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    UserInfo userInfo = userConfigService.getUserInfo(authentication.getName());
    Map<String, Object> data = new HashMap<>();
    Map<String, Object> user = new HashMap<>();
    user.put("userName", userInfo.getUsername());
    user.put("nickName", userInfo.getUsername());
    user.put("userId", userInfo.getId());
    data.put("user", user);
    data.put("roles", Collections.singletonList(userInfo.getRoleCode()));
    data.put("permissions", Collections.emptyList());
    return RestResult.restSuccess(data);
}
```

Add imports: `java.util.Map`, `java.util.HashMap`, `java.util.Collections`.

- [ ] **Step 3: Commit**

```bash
git add dbsyncer-web/src/main/java/org/dbsyncer/web/controller/index/IndexController.java
git commit -m "feat: add GET /index/getInfo endpoint for current user info

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 14: 前端 — 创建 api/login.ts

**Files:**
- Create: `dbsyncer-web-ui/src/api/login.ts`

- [ ] **Step 1: 创建 api/login.ts**

```typescript
import request from '@/utils/request'

export function login(username: string, password: string) {
  return request({
    url: '/login',
    headers: { isToken: false },
    method: 'post',
    data: { username, password },
  })
}

export function getInfo() {
  return request({
    url: '/index/getInfo',
    method: 'get',
  })
}

export function logout() {
  return request({
    url: '/logout',
    method: 'post',
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/api/login.ts
git commit -m "feat: add api/login.ts — login/getInfo/logout API functions

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 15: 前端 — 重构 stores/auth.ts → stores/user.ts

**Files:**
- Create: `dbsyncer-web-ui/src/stores/user.ts`
- Delete: `dbsyncer-web-ui/src/stores/auth.ts` (after verifying new store works)

- [ ] **Step 1: 创建 stores/user.ts**

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getInfo as getInfoApi } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const id = ref<string>('')
  const name = ref<string>('')
  const nickName = ref<string>('')
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  async function login(username: string, password: string): Promise<void> {
    const res = await loginApi(username, password) as any
    setToken(res.token)
    token.value = res.token
  }

  async function getInfo(): Promise<void> {
    const res = await getInfoApi() as any
    const user = res.user
    id.value = user.userId
    name.value = user.userName
    nickName.value = user.nickName || user.userName
    roles.value = res.roles || []
    permissions.value = res.permissions || []
  }

  async function doLogout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // ignore backend errors
    }
    token.value = ''
    roles.value = []
    permissions.value = []
    removeToken()
  }

  // 前端强制退出（不调后端）
  function resetState(): void {
    token.value = ''
    roles.value = []
    permissions.value = []
    removeToken()
  }

  return { token, id, name, nickName, roles, permissions, login, getInfo, doLogout, resetState }
})
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/stores/user.ts
git commit -m "feat: add stores/user.ts — JWT-based user state management

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 16: 前端 — 创建 permission.ts 路由守卫

**Files:**
- Create: `dbsyncer-web-ui/src/permission.ts`

- [ ] **Step 1: 创建 permission.ts（参考 RuoYi-Vue3 路由守卫 + 动态标题）**

```typescript
import router from './router'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'
import { useDynamicTitle } from '@/utils/dynamicTitle'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { isRelogin } from '@/utils/request'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login', '/register']

router.beforeEach((to, _from, next) => {
  NProgress.start()
  useDynamicTitle()

  if (getToken()) {
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else if (whiteList.includes(to.path)) {
      next()
    } else {
      const userStore = useUserStore()
      if (userStore.roles.length === 0) {
        isRelogin.show = true
        userStore.getInfo().then(() => {
          isRelogin.show = false
          next({ ...to, replace: true })
        }).catch(() => {
          userStore.resetState()
          ElMessage.error('获取用户信息失败，请重新登录')
          next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
          NProgress.done()
        })
      } else {
        next()
      }
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/permission.ts
git commit -m "feat: add permission.ts — route guard with JWT token check

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 17: 前端 — 简化 router/index.ts

**Files:**
- Modify: `dbsyncer-web-ui/src/router/index.ts`

- [ ] **Step 1: 重写 router/index.ts，移除 requiresAuth 守卫逻辑**

```typescript
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '首页' } },
      { path: 'connectors', name: 'Connectors', component: () => import('@/views/connector/ConnectorList.vue'), meta: { title: '连接器' } },
      { path: 'mappings', name: 'Mappings', component: () => import('@/views/mapping/MappingList.vue'), meta: { title: '同步任务' } },
      { path: 'mappings/:id', name: 'MappingEdit', component: () => import('@/views/mapping/MappingEdit.vue'), meta: { title: '编辑任务' } },
      { path: 'monitor', name: 'Monitor', component: () => import('@/views/monitor/MonitorView.vue'), meta: { title: '监控' } },
      { path: 'tasks', name: 'Tasks', component: () => import('@/views/task/TaskList.vue'), meta: { title: '任务' } },
      { path: 'users', name: 'Users', component: () => import('@/views/user/UserList.vue'), meta: { title: '用户' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
```

Key changes: 移除 `useAuthStore` 和 `beforeEach` hook（迁移到 `permission.ts`），移除 `meta.requiresAuth`，添加 `meta.title`。

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/router/index.ts
git commit -m "refactor: simplify router — move guard logic to permission.ts

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 18: 前端 — 改造 LoginView.vue

**Files:**
- Modify: `dbsyncer-web-ui/src/views/login/LoginView.vue`

- [ ] **Step 1: 重写 LoginView.vue script 部分**

Replace the `<script setup lang="ts">` section:

```typescript
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    ElMessage.error('用户名或密码错误')
  } finally {
    loading.value = false
  }
}
</script>
```

Template 保持不变。

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/views/login/LoginView.vue
git commit -m "refactor: update LoginView to use userStore.login and redirect support

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 19: 前端 — 改造 MainLayout.vue

**Files:**
- Modify: `dbsyncer-web-ui/src/views/layout/MainLayout.vue`

- [ ] **Step 1: 修改 MainLayout 使用 userStore**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => route.path)

async function handleLogout() {
  await userStore.doLogout()
  router.push('/login')
}
</script>
```

Template 中 `{{ authStore.username }}` 改为 `{{ userStore.name }}`。

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/views/layout/MainLayout.vue
git commit -m "refactor: update MainLayout to use userStore instead of authStore

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 20: 前端 — 改造 main.ts 导入 permission

**Files:**
- Modify: `dbsyncer-web-ui/src/main.ts`

- [ ] **Step 1: 在 main.ts 中添加 permission 导入**

Add `import './permission'` after `import router from './router'`:

```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './permission'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
```

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/src/main.ts
git commit -m "feat: register permission.ts guard in main.ts

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 21: 前端 — 重写 vite.config.ts（参考 RuoYi-Vue3）

**Files:**
- Modify: `dbsyncer-web-ui/vite.config.ts`

- [ ] **Step 1: 重写 vite.config.ts，采用 RuoYi-Vue3 的 loadEnv + 插件工厂 + /dev-api 代理模式**

```typescript
import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import createVitePlugins from './vite/plugins'

const baseUrl = 'http://127.0.0.1:18686'

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const { VITE_APP_ENV } = env

  return {
    base: VITE_APP_ENV === 'production' ? '/' : '/',
    plugins: createVitePlugins(env, command === 'build'),
    resolve: {
      alias: {
        '~': path.resolve(__dirname, './'),
        '@': path.resolve(__dirname, './src'),
      },
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue'],
    },
    build: {
      sourcemap: command === 'build' ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]',
        },
      },
    },
    server: {
      port: 5173,
      host: true,
      open: true,
      proxy: {
        '/dev-api': {
          target: baseUrl,
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, ''),
        },
      },
    },
  }
})
```

Note: 用 `/dev-api` 前缀代理模式替代之前的 12 条路径列表。Vite 自动 strip 前缀后转发到后端。生产环境 nginx 同理处理。

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web-ui/vite.config.ts
git commit -m "refactor: rewrite vite.config.ts — RuoYi-Vue3 loadEnv + plugin factory + /dev-api proxy

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 22: 前端 — 创建 api/connector.ts + 改造 ConnectorList.vue

**Files:**
- Create: `dbsyncer-web-ui/src/api/connector.ts`
- Modify: `dbsyncer-web-ui/src/views/connector/ConnectorList.vue`

- [ ] **Step 1: 创建 api/connector.ts**

```typescript
import request from '@/utils/request'

export function searchConnector(query: { pageNum: number; pageSize: number }) {
  return request({ url: '/connector/search', method: 'post', data: query })
}

export function addConnector(data: Record<string, any>) {
  return request({ url: '/connector/add', method: 'post', data })
}

export function editConnector(data: Record<string, any>) {
  return request({ url: '/connector/edit', method: 'post', data })
}

export function removeConnector(id: string) {
  return request({ url: '/connector/remove', method: 'post', data: { id } })
}

export function testConnector(id: string) {
  return request({ url: '/connector/test', method: 'post', data: { id } })
}

export function getConnectorList() {
  return request({ url: '/connector/list', method: 'get' })
}
```

- [ ] **Step 2: 改造 ConnectorList.vue**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchConnector, testConnector, removeConnector } from '@/api/connector'

const router = useRouter()
const loading = ref(false)
const items = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadData() {
  loading.value = true
  try {
    const res: any = await searchConnector({ pageNum: pageNum.value, pageSize: 10 })
    if (res?.data) {
      items.value = res.data.data || []
      total.value = res.data.total || 0
    }
  } finally { loading.value = false }
}

function handleAdd() { router.push('/connectors/add') }
function handleEdit(row: any) { /* TODO */ }

function handleTest(row: any) {
  testConnector(row.id).then(() => ElMessage.success('连接测试成功')).catch(() => ElMessage.error('连接测试失败'))
}

function handleRemove(row: any) {
  ElMessageBox.confirm('确定删除该连接器?', '提示', { type: 'warning' }).then(async () => {
    await removeConnector(row.id)
    ElMessage.success('删除成功')
    loadData()
  }).catch(() => {})
}

onMounted(loadData)
</script>
```

Template 保持不变。

- [ ] **Step 3: Commit**

```bash
git add dbsyncer-web-ui/src/api/connector.ts dbsyncer-web-ui/src/views/connector/ConnectorList.vue
git commit -m "refactor: extract connector API to api/connector.ts, simplify view

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 23: 前端 — 创建 api/mapping.ts + 改造 MappingList.vue + MappingEdit.vue

**Files:**
- Create: `dbsyncer-web-ui/src/api/mapping.ts`
- Modify: `dbsyncer-web-ui/src/views/mapping/MappingList.vue`
- Modify: `dbsyncer-web-ui/src/views/mapping/MappingEdit.vue`

- [ ] **Step 1: 创建 api/mapping.ts**

```typescript
import request from '@/utils/request'

export function searchMapping(query: { pageNum: number; pageSize: number }) {
  return request({ url: '/mapping/search', method: 'post', data: query })
}

export function addMapping(data: Record<string, any>) {
  return request({ url: '/mapping/add', method: 'post', data })
}

export function editMapping(data: Record<string, any>) {
  return request({ url: '/mapping/edit', method: 'post', data })
}

export function removeMapping(id: string) {
  return request({ url: '/mapping/remove', method: 'post', data: { id } })
}

export function startMapping(id: string) {
  return request({ url: '/mapping/start', method: 'post', data: { id } })
}

export function stopMapping(id: string) {
  return request({ url: '/mapping/stop', method: 'post', data: { id } })
}
```

- [ ] **Step 2: 改造 MappingList.vue script**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchMapping, startMapping, stopMapping, removeMapping } from '@/api/mapping'

const router = useRouter()
const loading = ref(false)
const items = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadData() {
  loading.value = true
  try {
    const res: any = await searchMapping({ pageNum: pageNum.value, pageSize: 10 })
    if (res?.data) {
      items.value = res.data.data || []
      total.value = res.data.total || 0
    }
  } finally { loading.value = false }
}

function handleAdd() { router.push('/mappings/add') }
function handleEdit(row: any) { router.push('/mappings/' + row.id) }

function handleStart(row: any) {
  startMapping(row.id).then(() => { ElMessage.success('启动成功'); loadData() }).catch(() => ElMessage.error('启动失败'))
}
function handleStop(row: any) {
  stopMapping(row.id).then(() => { ElMessage.success('停止成功'); loadData() }).catch(() => ElMessage.error('停止失败'))
}
function handleRemove(row: any) {
  ElMessageBox.confirm('确定删除?', '提示', { type: 'warning' }).then(async () => {
    await removeMapping(row.id)
    ElMessage.success('删除成功')
    loadData()
  }).catch(() => {})
}

onMounted(loadData)
</script>
```

- [ ] **Step 3: 改造 MappingEdit.vue script**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addMapping, editMapping } from '@/api/mapping'
import { searchConnector } from '@/api/connector'

const route = useRoute()
const router = useRouter()
const isNew = !route.params.id
const connectors = ref<any[]>([])
const form = reactive({ name: '', sourceConnectorId: '', targetConnectorId: '', model: 'full' })

onMounted(async () => {
  try {
    const res: any = await searchConnector({ pageNum: 1, pageSize: 100 })
    if (res?.data?.data) connectors.value = res.data.data
  } catch { /* ignore */ }
})

async function handleSave() {
  try {
    const url = isNew ? addMapping : editMapping
    await url(form as Record<string, any>)
    ElMessage.success('保存成功')
    router.push('/mappings')
  } catch {
    ElMessage.error('保存失败')
  }
}
</script>
```

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-web-ui/src/api/mapping.ts dbsyncer-web-ui/src/views/mapping/MappingList.vue dbsyncer-web-ui/src/views/mapping/MappingEdit.vue
git commit -m "refactor: extract mapping API to api/mapping.ts, simplify views

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 24: 前端 — 创建剩余 API 模块 + 改造对应 Views

**Files:**
- Create: `dbsyncer-web-ui/src/api/monitor.ts`
- Create: `dbsyncer-web-ui/src/api/task.ts`
- Create: `dbsyncer-web-ui/src/api/user.ts`
- Create: `dbsyncer-web-ui/src/api/plugin.ts`
- Create: `dbsyncer-web-ui/src/api/config.ts`
- Create: `dbsyncer-web-ui/src/api/system.ts`
- Create: `dbsyncer-web-ui/src/api/tableGroup.ts`
- Create: `dbsyncer-web-ui/src/api/openapi.ts`
- Modify: `dbsyncer-web-ui/src/views/monitor/MonitorView.vue`
- Modify: `dbsyncer-web-ui/src/views/task/TaskList.vue`
- Modify: `dbsyncer-web-ui/src/views/user/UserList.vue`
- Modify: `dbsyncer-web-ui/src/views/dashboard/DashboardView.vue`

- [ ] **Step 1: 创建 api/monitor.ts**

```typescript
import request from '@/utils/request'

export function queryData(query: Record<string, any>) {
  return request({ url: '/monitor/queryData', method: 'post', data: query })
}

export function queryLog(query: Record<string, any>) {
  return request({ url: '/monitor/queryLog', method: 'post', data: query })
}

export function syncMonitor() {
  return request({ url: '/monitor/sync', method: 'post' })
}

export function clearData() {
  return request({ url: '/monitor/clearData', method: 'post' })
}

export function clearLog() {
  return request({ url: '/monitor/clearLog', method: 'post' })
}

export function getMetric() {
  return request({ url: '/monitor/metric', method: 'get' })
}

export function getDashboard() {
  return request({ url: '/monitor/dashboard', method: 'get' })
}
```

- [ ] **Step 2: 创建 api/task.ts**

```typescript
import request from '@/utils/request'

export function searchTask(query: Record<string, any>) {
  return request({ url: '/task/list', method: 'post', data: query })
}

export function startTask(id: string) {
  return request({ url: '/task/start', method: 'post', data: { taskId: id } })
}

export function stopTask(id: string) {
  return request({ url: '/task/stop', method: 'post', data: { taskId: id } })
}

export function deleteTask(id: string) {
  return request({ url: '/task/delete', method: 'get', params: { taskId: id } })
}
```

- [ ] **Step 3: 创建 api/user.ts**

```typescript
import request from '@/utils/request'

export function searchUser(query: string) {
  return request({ url: '/user/search', method: 'post', data: query })
}

export function removeUser(id: string) {
  return request({ url: '/user/remove', method: 'post', data: { id } })
}
```

- [ ] **Step 4: 创建 api/plugin.ts, api/config.ts, api/system.ts, api/tableGroup.ts, api/openapi.ts**

api/plugin.ts:
```typescript
import request from '@/utils/request'

export function getPlugins() {
  return request({ url: '/plugin', method: 'get' })
}

export function uploadPlugin(data: FormData) {
  return request({ url: '/plugin/upload', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' } })
}
```

api/config.ts:
```typescript
import request from '@/utils/request'

export function getConfig() {
  return request({ url: '/config', method: 'get' })
}

export function uploadConfig(data: FormData) {
  return request({ url: '/config/upload', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' } })
}
```

api/system.ts:
```typescript
import request from '@/utils/request'

export function getSystemInfo() {
  return request({ url: '/system', method: 'get' })
}

export function editSystem(data: Record<string, any>) {
  return request({ url: '/system/edit', method: 'post', data })
}

export function generateRSA() {
  return request({ url: '/system/generateRSA', method: 'post' })
}
```

api/tableGroup.ts:
```typescript
import request from '@/utils/request'

export function searchTableGroup(query: Record<string, any>) {
  return request({ url: '/tableGroup/search', method: 'post', data: query })
}

export function addTableGroup(data: Record<string, any>) {
  return request({ url: '/tableGroup/add', method: 'post', data })
}

export function removeTableGroup(id: string) {
  return request({ url: '/tableGroup/remove', method: 'post', data: { id } })
}
```

api/openapi.ts:
```typescript
import request from '@/utils/request'

export function openApiLogin(username: string, password: string) {
  return request({ url: '/openapi/auth/login', method: 'post', headers: { isToken: false }, data: { username, password } })
}

export function openApiRefresh(token: string) {
  return request({ url: '/openapi/auth/refresh', method: 'post', data: { token } })
}

export function openApiDataSync(data: Record<string, any>) {
  return request({ url: '/openapi/data/sync', method: 'post', data })
}
```

- [ ] **Step 5: 改造 MonitorView.vue**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { queryData } from '@/api/monitor'

const loading = ref(false)
const dataItems = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadData() {
  loading.value = true
  try {
    const res: any = await queryData({ pageNum: pageNum.value, pageSize: 20 })
    if (res?.data) {
      dataItems.value = res.data.data || []
      total.value = res.data.total || 0
    }
  } finally { loading.value = false }
}

onMounted(loadData)
</script>
```

- [ ] **Step 6: 改造 TaskList.vue**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { searchTask, startTask, stopTask, deleteTask } from '@/api/task'

const loading = ref(false)
const items = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await searchTask('pageNum=1&pageSize=50')
    if (res?.data) items.value = res.data.data || []
  } finally { loading.value = false }
})

function handleStart(row: any) {
  startTask(row.id).then(() => ElMessage.success('启动成功'))
}
function handleStop(row: any) {
  stopTask(row.id).then(() => ElMessage.success('停止成功'))
}
function handleDelete(row: any) {
  deleteTask(row.id).then(() => { ElMessage.success('删除成功'); items.value = items.value.filter(i => i.id !== row.id) })
}
</script>
```

- [ ] **Step 7: 改造 UserList.vue**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchUser, removeUser } from '@/api/user'

const loading = ref(false)
const items = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await searchUser('pageNum=1&pageSize=50')
    if (res?.data) items.value = res.data.data || []
  } finally { loading.value = false }
})

function handleEdit(row: any) { /* TODO */ }
function handleRemove(row: any) {
  ElMessageBox.confirm('确定删除?', '提示', { type: 'warning' }).then(async () => {
    await removeUser(row.id)
    ElMessage.success('删除成功')
    items.value = items.value.filter(i => i.id !== row.id)
  }).catch(() => {})
}
</script>
```

- [ ] **Step 8: 改造 DashboardView.vue**

Replace `<script setup lang="ts">`:

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMetric } from '@/api/monitor'

const cpu = ref('--')
const memory = ref('--')
const threads = ref('--')

onMounted(async () => {
  try {
    const res: any = await getMetric()
    if (res?.data) {
      const d = res.data
      cpu.value = (d.cpu?.totalPercent || 0) + '%'
      memory.value = (d.memory?.jvmUsed || '--') + ' / ' + (d.memory?.jvmTotal || '--')
      threads.value = (d.threadsLive || 0) + ' / ' + (d.threadsPeak || 0)
    }
  } catch { /* dashboard load failed */ }
})
</script>
```

- [ ] **Step 9: Commit**

```bash
git add dbsyncer-web-ui/src/api/monitor.ts dbsyncer-web-ui/src/api/task.ts dbsyncer-web-ui/src/api/user.ts dbsyncer-web-ui/src/api/plugin.ts dbsyncer-web-ui/src/api/config.ts dbsyncer-web-ui/src/api/system.ts dbsyncer-web-ui/src/api/tableGroup.ts dbsyncer-web-ui/src/api/openapi.ts dbsyncer-web-ui/src/views/monitor/MonitorView.vue dbsyncer-web-ui/src/views/task/TaskList.vue dbsyncer-web-ui/src/views/user/UserList.vue dbsyncer-web-ui/src/views/dashboard/DashboardView.vue
git commit -m "refactor: add all API modules, update all views to use API functions

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 25: 清理 — 删除旧文件

**Files:**
- Delete: `dbsyncer-web-ui/src/api/index.ts`
- Delete: `dbsyncer-web-ui/src/stores/auth.ts`

- [ ] **Step 1: 确认没有文件再 import 旧模块**

```bash
grep -rn "from '@/api'\|from '@/stores/auth'" /Users/work2021/DataCenterRespo/hc-dbsync/dbsyncer-web-ui/src --include="*.ts" --include="*.vue"
```

Expected: no output (no remaining references).

- [ ] **Step 2: 删除旧 api/index.ts**

```bash
rm /Users/work2021/DataCenterRespo/hc-dbsync/dbsyncer-web-ui/src/api/index.ts
```

- [ ] **Step 3: 删除旧 stores/auth.ts**

```bash
rm /Users/work2021/DataCenterRespo/hc-dbsync/dbsyncer-web-ui/src/stores/auth.ts
```

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-web-ui/src/api/index.ts dbsyncer-web-ui/src/stores/auth.ts
git commit -m "chore: remove old api/index.ts and stores/auth.ts

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 26: 验证 — TypeScript 编译检查

- [ ] **Step 1: 运行 vue-tsc 类型检查**

```bash
cd /Users/work2021/DataCenterRespo/hc-dbsync/dbsyncer-web-ui && npx vue-tsc --noEmit
```

Expected: 0 errors. Fix any type errors found.

- [ ] **Step 2: 运行 Vite build 检查**

```bash
cd /Users/work2021/DataCenterRespo/hc-dbsync/dbsyncer-web-ui && npx vite build
```

Expected: build succeeds with no errors.

- [ ] **Step 3: Commit any fixes**

```bash
git add -A dbsyncer-web-ui/src/
git commit -m "fix: type errors and build issues from refactoring

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 1 | CLEAR | SELECTIVE_EXPANSION: +settings.ts, +env staging, +vite plugins, +dynamicTitle |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | CLEAR | 2 issues: 1 P1 fixed (RestResult.restSuccess signature), 1 P2 noted (401 relogin on failed login) |

**CODEX:** skipped (Codex not available)
**VERDICT:** CEO + ENG CLEARED — ready to implement

**NOT in scope:**
- Backend `/logout` endpoint changes (existing already works)
- E2E tests (plan covers vue-tsc + vite build verification)
- Dynamic sidebar menu from backend (static menu kept)
- TagsView / breadcrumb components (deferred to future PR)

**What already exists (reused):**
- `WebAppConfig.java` auth provider + logout handler — kept, adds JWT filter before form login
- `RestResult` response wrapper — used throughout, no change needed
- Existing view templates — only `<script setup>` sections modified
- Existing Element Plus icons registration in `main.ts` — kept

NO UNRESOLVED DECISIONS
