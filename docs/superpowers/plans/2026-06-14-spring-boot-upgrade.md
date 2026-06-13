# Phase 1: Spring Boot 3.5.x + JDK 21 Upgrade Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade DBSyncer from Spring Boot 2.5.14 / JDK 8 to Spring Boot 3.5.x / JDK 21 while keeping all existing functionality unchanged. Compile passes, MySQL→MySQL full+incremental sync works.

**Architecture:** Incremental upgrade — POM versions first, then javax→jakarta automated replacement, then Spring Security 6 manual migration, then Thymeleaf template validation, then safety cleanup (p12 removal). Each task produces a compilable state.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security 6.x, Jakarta EE 10, Thymeleaf 3.1, Log4j2 (SB-managed), Elasticsearch REST Client 7.17.x (explicit lock)

---

## Pre-flight

### Task 0: Verify current build

- [ ] **Step 1: Run Maven build on current codebase**

```bash
cd /Users/work2021/DataCenterRespo/dbsyncer
mvn clean compile -DskipTests 2>&1 | tail -30
```

Expected: BUILD SUCCESS. If not, note any pre-existing errors before starting upgrade.

- [ ] **Step 2: Record Java 8 javac version for reference**

```bash
javac -version
mvn --version
```

---

### Task 1: Root POM version upgrade

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Update Java version properties**

Replace in `pom.xml`:

```xml
<maven.compiler.source>1.8</maven.compiler.source>
<maven.compiler.target>1.8</maven.compiler.target>
<maven.compiler.compilerVersion>1.8</maven.compiler.compilerVersion>
<java.version>1.8</java.version>
```

with:

```xml
<maven.compiler.release>21</maven.compiler.release>
<java.version>21</java.version>
```

Also remove `maven.compiler.source`, `maven.compiler.target`, `maven.compiler.compilerVersion` — only `maven.compiler.release` is needed for JDK 9+.

- [ ] **Step 2: Update Spring Boot version**

```xml
<spring-boot.version>3.5.3</spring-boot.version>
```

(Replace the 2.5.14 line. If 3.5.3 is not the exact latest, check with `mvn versions:display-dependency-updates`.)

- [ ] **Step 3: Update log4j2 to SB-managed**

Remove the `<log4j2.version>2.17.1</log4j2.version>` property. SB 3.5 manages log4j2 version through its BOM. Remove the 5 `<dependency>` entries for log4j (log4j-api, log4j-slf4j-impl, log4j-to-slf4j, log4j-core, log4j-jul) from `<dependencyManagement>` — they stay in the module POMs that need them (SB manages the version automatically via the BOM's managed dependencies, and the modules will pick up the BOM version).

Actually, keep the log4j dependency entries in `dependencyManagement` but remove the version tags — let them inherit from SB BOM. Or better: remove them from `dependencyManagement` entirely since modules (`dbsyncer-common`, `connector-base`, `connector-kafka`) already declare `spring-boot-starter-log4j2` which pulls in the correct versions.

- [ ] **Step 4: Remove Spring Boot-managed dependency versions from dependencyManagement**

The following are managed by SB 3.5.x BOM and don't need explicit versions in `<dependencyManagement>`:

```xml
<!-- REMOVE version tags for these, SB BOM handles them: -->
<dependency><groupId>com.alibaba.fastjson2</groupId><artifactId>fastjson2</artifactId></dependency>
<dependency><groupId>com.google.protobuf</groupId><artifactId>protobuf-java</artifactId></dependency>
<dependency><groupId>junit</groupId><artifactId>junit</artifactId></dependency>
```

Actually KEEP explicit versions for the database drivers and connectors that SB does NOT manage:
- mysql-connector-java 8.0.21
- ojdbc8 21.6.0.0
- mssql-jdbc 8.4.1.jre8 (NOTE: needs upgrade to jre17/jre21 variant)
- postgresql 42.3.3
- mysql-binlog-connector-java 0.30.1
- kafka-clients 0.11.0.0
- lucene-analyzers-smartcn 8.8.0
- jts-core 1.19.0
- antlr4-runtime 4.7.2
- jsqlparser 4.9
- commons-fileupload 1.4
- commons-io 2.7
- commons-codec 1.15
- postgis-jdbc 2.5.1

These are ex-SB-management or not managed at all. SB 3.5 BOM manages: Spring ecosystem, Jackson, Log4j2, HikariCP, Thymeleaf, commons-lang3, javax/jakarta APIs, JUnit 5 (JUnit 4 needs explicit).

- [ ] **Step 5: Add Jakarta Servlet API dependency for servlet-based modules**

Add to `<dependencyManagement>`:

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>
```

This replaces `javax.servlet:javax.servlet-api` (which no longer exists in SB 3.5).

- [ ] **Step 6: Commit**

```bash
git add pom.xml
git commit -m "chore: upgrade root POM to JDK 21 and Spring Boot 3.5.3"
```

---

### Task 2: Module POM dependency updates

**Files:**
- Modify: `dbsyncer-common/pom.xml`
- Modify: `dbsyncer-web/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-base/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-elasticsearch/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-mysql/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-kafka/pom.xml`

- [ ] **Step 1: dbsyncer-common/pom.xml — replace javax.annotation with jakarta.annotation**

Find:
```xml
<groupId>javax.annotation</groupId>
<artifactId>javax.annotation-api</artifactId>
```

Replace with:
```xml
<groupId>jakarta.annotation</groupId>
<artifactId>jakarta.annotation-api</artifactId>
```

Remove the `<version>` tag if present (SB 3.5 BOM manages this).

- [ ] **Step 2: dbsyncer-web/pom.xml — add jakarta.servlet-api**

Add:
```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 3: dbsyncer-connector/dbsyncer-connector-elasticsearch/pom.xml — lock ES version**

SB 3.5 dropped `elasticsearch-rest-high-level-client` from its BOM. Add explicit version:

```xml
<properties>
    <elasticsearch.version>7.17.28</elasticsearch.version>
</properties>
```

And update the dependency:
```xml
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-high-level-client</artifactId>
    <version>${elasticsearch.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.elasticsearch</groupId>
            <artifactId>jna</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

- [ ] **Step 4: dbsyncer-connector/dbsyncer-connector-mysql/pom.xml — update mssql-jdbc to JDK 21 variant**

Note: mssql-jdbc 8.4.1.jre8 is in the Oracle connector. Check if it still compiles with JDK 21. If not:

```xml
<!-- In connector-oracle/pom.xml, update if needed -->
<groupId>com.microsoft.sqlserver</groupId>
<artifactId>mssql-jdbc</artifactId>
<version>12.8.1.jre11</version> <!-- JRE 11 variant works on JDK 21 -->
```

- [ ] **Step 5: Commit**

```bash
git add dbsyncer-common/pom.xml dbsyncer-web/pom.xml dbsyncer-connector/dbsyncer-connector-elasticsearch/pom.xml
git commit -m "chore: update module POMs for SB 3.5.x compatibility"
```

---

### Task 3: javax → jakarta automated replacement (all modules)

**Files:**
- Modify: ~73 files across all modules

- [ ] **Step 1: Run automated javax→jakarta replacement**

```bash
cd /Users/work2021/DataCenterRespo/dbsyncer
find . -name "*.java" -not -path "*/target/*" -not -path "*/.git/*" | \
  xargs sed -i '' \
    -e 's/import javax\.annotation\./import jakarta.annotation./g' \
    -e 's/import javax\.servlet\./import jakarta.servlet./g' \
    -e 's/import javax\.xml\./import jakarta.xml./g' \
    -e 's/import javax\.inject\./import jakarta.inject./g' \
    -e 's/import javax\.persistence\./import jakarta.persistence./g'
```

- [ ] **Step 2: Verify no javax imports remain**

```bash
grep -rn "import javax\." --include="*.java" . | grep -v "target/" | grep -v ".git/"
```

Expected: empty output, or only `javax.sql.*` / `javax.crypto.*` / `javax.management.*` (JDK-internal packages, not Jakarta EE — these stay as-is).

- [ ] **Step 3: Handle javax.sql in SimpleDataSource**

`SimpleDataSource.java` imports `javax.sql.DataSource`. This is a JDK interface in `java.sql` package but `javax.sql` is also part of JDK (not Jakarta EE). If sed replaced `javax.sql` → `jakarta.sql`, revert it:

```bash
git diff dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/SimpleDataSource.java
# If it changed javax.sql to jakarta.sql, revert:
sed -i '' 's/jakarta\.sql\./javax.sql./g' dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/SimpleDataSource.java
```

- [ ] **Step 4: Attempt compile**

```bash
mvn clean compile -DskipTests 2>&1 | tail -50
```

- [ ] **Step 5: Fix any remaining javax→jakarta import errors**

If compile errors mention missing javax.* classes, manually fix those files to use jakarta equivalents.

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "refactor: migrate javax.* to jakarta.* for Spring Boot 3.x"
```

---

### Task 4: Spring Security 6 migration

**Files:**
- Modify: `dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java`
- Modify: `dbsyncer-web/src/main/java/org/dbsyncer/web/config/SecurityConfig.java` (if exists)

- [ ] **Step 1: Replace WebAppConfig — remove WebSecurityConfigurerAdapter, use @Bean style**

The current `WebAppConfig.java:45` extends `WebSecurityConfigurerAdapter` and implements `AuthenticationProvider`, `HttpSessionListener`. Replace with:

```java
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
import org.springframework.security.config.http.SessionCreationPolicy;
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
```

Key changes:
- No more `extends WebSecurityConfigurerAdapter` (removed in Security 6)
- `HttpSecurity` uses **Lambda DSL** (`.csrf(csrf -> csrf.disable())`)
- `antMatchers()` → `requestMatchers()`
- `AuthenticationProvider` extracted to inner class (used to be `implements AuthenticationProvider`)
- `HttpSessionListener` extracted to `@Bean`
- `@Resource` imported from `jakarta.annotation` (done in Task 3)

- [ ] **Step 2: Attempt compile**

```bash
cd /Users/work2021/DataCenterRespo/dbsyncer
mvn clean compile -pl dbsyncer-web -am -DskipTests 2>&1 | tail -30
```

- [ ] **Step 3: Fix any Security API compilation errors**

Common issues:
- `and()` method removed from Security 6 DSL — already handled by Lambda DSL
- `cors()` and `headers()` config — if any, use Lambda style

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java
git commit -m "refactor: migrate Spring Security to 6.x component-based config"
```

---

### Task 5: Thymeleaf template validation

**Files:**
- Check: `dbsyncer-web/src/main/resources/public/**/*.html` (~46 files)

Thymeleaf 3.1 (bundled with SB 3.5) is largely backward-compatible with Thymeleaf 2.x templates. Key checks:

- [ ] **Step 1: Check for deprecated `th:include` usage**

`th:include` was removed in Thymeleaf 3.0. If any template uses it, replace with `th:replace`:

```bash
grep -rn "th:include" dbsyncer-web/src/main/resources/public/ --include="*.html"
```

If found, replace `th:include="template :: fragment"` with `th:replace="template :: fragment"`.

- [ ] **Step 2: Check for `layout:decorator` usage**

```bash
grep -rn "layout:decorator\|xmlns:layout" dbsyncer-web/src/main/resources/public/ --include="*.html"
```

If found, replace with `layout:decorate` (Thymeleaf 3.x syntax).

- [ ] **Step 3: Verify templates compile in application context**

```bash
mvn clean package -DskipTests 2>&1 | grep -i "thymeleaf\|template\|error"
```

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-web/src/main/resources/public/
git commit -m "fix: update Thymeleaf templates for 3.x compatibility"
```

---

### Task 6: Remove dbsyncer.p12 and clean up SSL config

**Files:**
- Delete: `dbsyncer-web/src/main/resources/dbsyncer.p12`
- Modify: `dbsyncer-web/src/main/resources/application.properties`

- [ ] **Step 1: Delete the p12 file**

```bash
rm dbsyncer-web/src/main/resources/dbsyncer.p12
```

- [ ] **Step 2: Replace SSL config block in application.properties**

Replace lines 6-19 in `application.properties`:

```properties
# 启用SSL配置(false-关闭; true-开启)
server.ssl.enabled=false
# 指定外部配置文件
server.ssl.key-store=conf/dbsyncer.p12
server.ssl.key-store-password=dbsyncer
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=dbsyncer

# 启用http端口(false-关闭; true-开启)
server.http.enabled=false
# server.ssl.enabled和server.http.enabled同时开启生效
server.http.port=8080
# 强制http重定向https端口(false-关闭; true-开启)
server.http.redirect=false
```

with:

```properties
# ============================================================
# HTTPS 配置（默认关闭，生产环境请替换为自己的证书）
# 1. 生成证书: keytool -genkeypair -alias dbsyncer -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore conf/dbsyncer.p12 -validity 3650
# 2. 开启以下配置:
#   server.ssl.enabled=true
#   server.ssl.key-store=conf/dbsyncer.p12
#   server.ssl.key-store-password=your_password
#   server.ssl.key-store-type=PKCS12
#   server.ssl.key-alias=dbsyncer
# 3. (可选) HTTP→HTTPS 重定向:
#   server.http.enabled=true
#   server.http.port=8080
#   server.http.redirect=true
# ============================================================
server.ssl.enabled=false
```

- [ ] **Step 3: Commit**

```bash
git add dbsyncer-web/src/main/resources/dbsyncer.p12 dbsyncer-web/src/main/resources/application.properties
git commit -m "chore: remove bundled dbsyncer.p12, replace with HTTPS setup docs"
```

---

### Task 7: Full project compile and fix residual errors

- [ ] **Step 1: Full compile**

```bash
cd /Users/work2021/DataCenterRespo/dbsyncer
mvn clean compile -DskipTests 2>&1 | tee /tmp/dbsyncer-build.log
```

- [ ] **Step 2: Fix remaining compile errors**

```bash
grep "ERROR" /tmp/dbsyncer-build.log | grep -v "WARNING" | head -30
```

Common residual issues:
- **Maven surefire plugin version** — SB 3.5 BOM manages this, but if JUnit 4 tests fail, add: `<junit-vintage-engine>` dependency for JUnit 4 backward compat in SB 3.5
- **Thymeleaf expression syntax** — `#strings.isEmpty()` → `#strings.isEmpty()` (no change in 3.x, but verify)
- **FastJSON2** — SB 3.5 does not manage FastJSON2. Ensure `<version>2.0.22</version>` stays in `<dependencyManagement>`

- [ ] **Step 3: Iterate until BUILD SUCCESS**

For each error category, create a fix commit.

- [ ] **Step 4: Final commit**

```bash
git add -u
git commit -m "fix: resolve remaining SB 3.5.x compile errors"
```

---

### Task 8: Full package and smoke test

- [ ] **Step 1: Full Maven package**

```bash
mvn clean package -DskipTests 2>&1 | tail -20
```

Expected: BUILD SUCCESS, with `dbsyncer-web/target/dbsyncer-web-2.0.8.zip` or similar.

- [ ] **Step 2: Verify the packaged ZIP/JAR structure**

```bash
jar tf dbsyncer-web/target/dbsyncer-web-*.jar 2>/dev/null | grep -E "Application\.class|WebAppConfig\.class|META-INF/" | head -10
```

- [ ] **Step 3: Start the application**

```bash
java -jar dbsyncer-web/target/dbsyncer-web-*.jar --server.port=18687 2>&1 &
# Wait for startup
sleep 15
curl -s http://localhost:18687/login.html | head -5
```

Expected: Returns HTML content of login page.

- [ ] **Step 4: Verify health endpoint**

```bash
curl -s http://localhost:18687/app/health
```

Expected: `{"status":"UP",...}`

- [ ] **Step 5: Kill test instance**

```bash
kill %1 2>/dev/null || true
```

- [ ] **Step 6: Commit**

```bash
git commit --allow-empty -m "chore: smoke test passed — app starts and serves login page"
```

---

### Task 9: MySQL→MySQL end-to-end sync test

**Prerequisites:** Local MySQL instance running with test database.

- [ ] **Step 1: Start the application with MySQL storage**

```bash
java -jar dbsyncer-web/target/dbsyncer-web-*.jar --server.port=18687 --dbsyncer.storage.type=mysql --dbsyncer.storage.mysql.url=jdbc:mysql://127.0.0.1:3306/dbsyncer_test?createDatabaseIfNotExist=true --dbsyncer.storage.mysql.username=root --dbsyncer.storage.mysql.password=root 2>&1 &
sleep 15
```

- [ ] **Step 2: Create test tables via API or direct SQL**

Create a simple source table and target table in MySQL:

```sql
CREATE DATABASE IF NOT EXISTS dbsyncer_source;
CREATE DATABASE IF NOT EXISTS dbsyncer_target;

USE dbsyncer_source;
CREATE TABLE IF NOT EXISTS test_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

USE dbsyncer_target;
CREATE TABLE IF NOT EXISTS test_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert test data
USE dbsyncer_source;
INSERT INTO test_users (name, email) VALUES
('Alice', 'alice@test.com'),
('Bob', 'bob@test.com'),
('Charlie', 'charlie@test.com');
```

- [ ] **Step 3: Configure connector and mapping via REST API**

```bash
# Add source connector
curl -s -X POST http://localhost:18687/connector/add \
  -d "connectorType=mysql" \
  -d "url=jdbc:mysql://127.0.0.1:3306/dbsyncer_source" \
  -d "username=root" \
  -d "password=root" \
  -d "name=source_mysql"

# Add target connector (similar)
# Add mapping (source → target with table group)
# Start mapping
```

Note: If this is too complex via curl, the core verification is:
1. App starts ✅
2. Can log in ✅  
3. Can add connector ✅
4. Can create and start a mapping ✅
5. Full sync copies 3 rows ✅
6. Incremental sync picks up new INSERT ✅

- [ ] **Step 4: Verify full sync copied data**

```sql
USE dbsyncer_target;
SELECT count(*) FROM test_users;  -- Expected: 3
```

- [ ] **Step 5: Verify incremental sync**

```sql
USE dbsyncer_source;
INSERT INTO test_users (name, email) VALUES ('Diana', 'diana@test.com');
-- Wait 5 seconds
USE dbsyncer_target;
SELECT count(*) FROM test_users;  -- Expected: 4
```

- [ ] **Step 6: Cleanup**

```bash
kill %1 2>/dev/null || true
```

- [ ] **Step 7: Record results**

If all steps pass → Phase 1 verified. If any step fails, document the error and fix.

---

### Task 10: Tag Phase 1 complete

- [ ] **Step 1: Push all commits to remote**

```bash
git push origin master
```

- [ ] **Step 2: Create annotated tag**

```bash
git tag -a v3.0.0-phase1 -m "Phase 1: Spring Boot 3.5.x + JDK 21 upgrade"
git push origin v3.0.0-phase1
```

---

## Phase 1.5 — Frontend Rewrite (parallel track, separate PR)

Not part of this plan. Will be a separate plan file. Core decisions already made:
- Vue 3 + Vite + TypeScript + Pinia + Element Plus
- Core pages first: Login, Dashboard, Connectors, Mapping (with all 8 sub-tabs), Monitor
- Reuse existing REST JSON API endpoints
- Located in `dbsyncer-web-ui/` (new module, separate from `dbsyncer-web`)

## Phase 2 — Performance Optimization

Not part of this plan. Will be a separate plan file. Based on `docs/performance.md` analysis.
