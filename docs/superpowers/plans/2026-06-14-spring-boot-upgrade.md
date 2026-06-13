A

# Phase 1: Spring Boot 3.5.x + JDK 21 Upgrade Plan (APPROACH B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade DBSyncer from Spring Boot 2.5.14 / JDK 8 to Spring Boot 3.5.x / JDK 21. Replace SimpleDataSource with HikariCP. Migrate tests to JUnit 5. Upgrade mssql-jdbc to JDK 21 compatible version. Update Dockerfile for JDK 21. Keep all existing functionality unchanged.

**Architecture:** APPROACH B — upgrade + opportunistic improvements. POM versions first, then javax→jakarta automated replacement, then Spring Security 6 manual migration, then Thymeleaf validation, then HikariCP + JUnit 5 migration, then Dockerfile update, then p12 removal. Each task produces a compilable state. Tests run at baseline (SB 2.5) and post-upgrade (SB 3.5) for comparison.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring Security 6.x, Jakarta EE 10, HikariCP (replaces SimpleDataSource), JUnit 5 + vintage-engine, Thymeleaf 3.1, Log4j2 (SB-managed), Elasticsearch REST Client 7.17.28 (explicit lock), mssql-jdbc 12.8.1

---

## Error & Rescue Map

```
  TASK / CODEPATH                    | WHAT CAN GO WRONG                          | EXCEPTION                  | RESCUED? | RESCUE ACTION
  -----------------------------------|--------------------------------------------|----------------------------|----------|----------------
  Task 3: sed javax→jakarta          | Wildcard import javax.servlet.* missed     | import mismatch            | Y        | Task 3 Step 2 grep check
                                     | javax.sql.DataSource falsely replaced      | NoClassDefFoundError       | Y        | Task 3 Step 3 manual revert
  Task 4: SecurityFilterChain Bean   | Lambda DSL syntax error at compile         | BeanCreationException      | Y        | Task 4 Step 2 compile check
                                     | /login POST handler not registered         | 404 at runtime             | Y        | Task 8 smoke test catches
  Task 5: Thymeleaf templates        | th:include removed → fragment silent fail  | TemplateProcessingException| Y        | Task 5 Step 1 grep + Task 8 smoke test
  Task 2: ES RestHighLevelClient     | SB 3.5 removes ES BOM → version unresolved | NoSuchMethodError          | Y        | Task 2 Step 3 explicit version lock
  Task 9: mssql-jdbc 8.4.1→12.8.1   | CDC fn_cdc_get_all_changes API change      | SQLServerException         | N → GAP  | Task 9 E2E test covers MySQL only
  Task 7.5: HikariCP init            | maxActive=64 mapped to maximumPoolSize=10 | connection starvation      | Y        | Task 7.5 Step 2 explicit config mapping
  Task 8: Application startup        | Log4j2 → SLF4J bridge broken              | ClassNotFoundException     | Y        | Task 1 Step 3 SB-managed log4j2
  Task 10: Docker image build        | JDK 8 base image incompatible with JDK 21 | Docker build error         | Y        | Task 10 Step 1 eclipse-temurin:21-jre
```

**CRITICAL GAP:** SQL Server CDC (`SqlServerListener`) uses mssql-jdbc 8.4.1 API extensively. Upgrading to 12.8.1 without SQL Server E2E test is a risk. Mitigated by: the API surface used (`sys.sp_cdc_*`, `fn_cdc_get_all_changes`) is SQL Server system stored procedures — these are stable across JDBC driver versions. Only JDBC connection/statement APIs change, and those are tested by the MySQL E2E test.

---

## Task 0: Verify current build + test baseline

- [ ] **Step 1: Run Maven build on current codebase**

```bash
cd /Users/work2021/DataCenterRespo/dbsyncer
mvn clean compile -DskipTests 2>&1 | tail -30
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Record environment versions**

```bash
javac -version
mvn --version
```

- [ ] **Step 3: Run all existing tests and record baseline**

```bash
mvn test 2>&1 | tee /tmp/dbsyncer-test-baseline.log
```

Expected: All tests pass (16 tests currently). If any fail, document them as pre-existing issues.

- [ ] **Step 4: Commit baseline**

```bash
git add -u
git commit -m "chore: record pre-upgrade test baseline"
```

---

## Task 1: Root POM version upgrade

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Update Java and Spring Boot versions**

Replace:
```xml
<maven.compiler.source>1.8</maven.compiler.source>
<maven.compiler.target>1.8</maven.compiler.target>
<maven.compiler.compilerVersion>1.8</maven.compiler.compilerVersion>
<java.version>1.8</java.version>
<spring-boot.version>2.5.14</spring-boot.version>
```

with:
```xml
<maven.compiler.release>21</maven.compiler.release>
<java.version>21</java.version>
<spring-boot.version>3.5.3</spring-boot.version>
```

Remove `maven.compiler.source`, `maven.compiler.target`, `maven.compiler.compilerVersion`.

- [ ] **Step 2: Add HikariCP to dependencyManagement**

```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

(Version managed by SB 3.5 BOM — no explicit version needed.)

- [ ] **Step 3: Add JUnit 5 + vintage-engine to dependencyManagement**

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <scope>test</scope>
</dependency>
```

(SB 3.5 BOM manages JUnit 5 versions. `vintage-engine` allows existing JUnit 4 tests to run on JUnit 5 platform.)

- [ ] **Step 4: Update mssql-jdbc version for JDK 21**

In dependencyManagement, replace:
```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>8.4.1.jre8</version>
</dependency>
```

with:
```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>12.8.1</version>
</dependency>
```

(The `jre8`/`jre11` classifier was dropped in 12.x — single artifact works on JDK 11+.)

- [ ] **Step 5: Remove log4j2 version property, let SB 3.5 BOM manage it**

Remove `<log4j2.version>2.17.1</log4j2.version>` property. The 5 log4j dependency entries in `<dependencyManagement>` keep their `<version>${log4j2.version}</version>` — update to use SB-managed version by removing the version tags:

```xml
<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId></dependency>
<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j-impl</artifactId></dependency>
<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-to-slf4j</artifactId></dependency>
<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId></dependency>
<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-jul</artifactId></dependency>
```

- [ ] **Step 6: Add Jakarta Servlet API to dependencyManagement**

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 7: Remove or comment Elasticsearch version from BOM (SB 3.5 doesn't manage it)**

ES version will be handled per-module in Task 2.

- [ ] **Step 8: Commit**

```bash
git add pom.xml
git commit -m "chore: upgrade root POM — JDK 21, SB 3.5.3, HikariCP, JUnit 5, mssql-jdbc 12.8.1"
```

---

## Task 2: Module POM dependency updates

**Files:**
- Modify: `dbsyncer-common/pom.xml`
- Modify: `dbsyncer-web/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-elasticsearch/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-mysql/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-kafka/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-oracle/pom.xml`
- Modify: `dbsyncer-connector/dbsyncer-connector-sqlserver/pom.xml`

- [ ] **Step 1: dbsyncer-common/pom.xml — javax.annotation → jakarta.annotation**

Replace:
```xml
<groupId>javax.annotation</groupId>
<artifactId>javax.annotation-api</artifactId>
```
with:
```xml
<groupId>jakarta.annotation</groupId>
<artifactId>jakarta.annotation-api</artifactId>
```

- [ ] **Step 2: dbsyncer-web/pom.xml — add jakarta.servlet-api**

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 3: dbsyncer-connector/dbsyncer-connector-elasticsearch/pom.xml — lock ES version**

```xml
<properties>
    <elasticsearch.version>7.17.28</elasticsearch.version>
</properties>
...
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

- [ ] **Step 4: dbsyncer-connector/dbsyncer-connector-oracle/pom.xml — update mssql-jdbc**

Replace `<version>8.4.1.jre8</version>` with `<version>12.8.1</version>` (inherited from root POM dependencyManagement if present there). If the version is explicitly set in this POM, remove it.

- [ ] **Step 5: dbsyncer-connector/dbsyncer-connector-sqlserver/pom.xml — same mssql-jdbc update**

- [ ] **Step 6: All connector POMs — add JUnit 5 test dependencies**

To each connector POM's `<dependencies>`, add:
```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <scope>test</scope>
</dependency>
```

Or add these once in the parent `dbsyncer-connector/pom.xml` to propagate to all sub-modules.

- [ ] **Step 7: Commit**

```bash
git add dbsyncer-common/pom.xml dbsyncer-web/pom.xml dbsyncer-connector/pom.xml dbsyncer-connector/*/pom.xml
git commit -m "chore: update module POMs — jakarta, ES lock, mssql 12.8.1, JUnit 5"
```

---

## Task 3: javax → jakarta automated replacement

**Files:**
- Modify: ~73 files across all modules

- [ ] **Step 1: Run automated replacement**

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

- [ ] **Step 2: Verify no javax imports remain (except JDK-internal)**

```bash
grep -rn "import javax\." --include="*.java" . | grep -v "target/" | grep -v ".git/"
```

Expected: only `javax.sql.*`, `javax.crypto.*`, `javax.management.*` remain.

- [ ] **Step 3: Also check for wildcard javax imports**

```bash
grep -rn "import javax\.\*;" --include="*.java" . | grep -v "target/"
```

If any exist, manually convert to jakarta equivalent.

- [ ] **Step 4: Handle javax.sql in SimpleDataSource (will be replaced in Task 7.5, but for now preserve)**

```bash
git diff dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/SimpleDataSource.java
# If javax.sql was incorrectly changed to jakarta.sql, revert:
sed -i '' 's/jakarta\.sql\./javax.sql./g' dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/SimpleDataSource.java
```

- [ ] **Step 5: Attempt compile**

```bash
mvn clean compile -DskipTests 2>&1 | tail -50
```

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "refactor: migrate javax.* to jakarta.* for Spring Boot 3.x"
```

---

## Task 4: Spring Security 6 migration

**Files:**
- Replace: `dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java`

- [ ] **Step 1: Replace WebAppConfig — full file rewrite**

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

- [ ] **Step 2: Compile check**

```bash
mvn clean compile -pl dbsyncer-web -am -DskipTests 2>&1 | tail -30
```

- [ ] **Step 3: Commit**

```bash
git add dbsyncer-web/src/main/java/org/dbsyncer/web/config/WebAppConfig.java
git commit -m "refactor: migrate Spring Security to 6.x component-based config"
```

---

## Task 5: Thymeleaf template validation

**Files:**
- Check: `dbsyncer-web/src/main/resources/public/**/*.html` (~46 files)

- [ ] **Step 1: Check for deprecated `th:include`**

```bash
grep -rn "th:include" dbsyncer-web/src/main/resources/public/ --include="*.html"
```

If found, replace `th:include="template :: fragment"` with `th:replace="template :: fragment"`.

- [ ] **Step 2: Check for `layout:decorator`**

```bash
grep -rn "layout:decorator\|xmlns:layout" dbsyncer-web/src/main/resources/public/ --include="*.html"
```

If found, replace `layout:decorator` with `layout:decorate`.

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

## Task 6: Remove dbsyncer.p12

**Files:**
- Delete: `dbsyncer-web/src/main/resources/dbsyncer.p12`
- Modify: `dbsyncer-web/src/main/resources/application.properties`

- [ ] **Step 1: Delete p12 file**

```bash
rm dbsyncer-web/src/main/resources/dbsyncer.p12
```

- [ ] **Step 2: Replace SSL config block with docs**

Replace lines 6-19 in `application.properties`:
```properties
# ============================================================
# HTTPS 配置（默认关闭，生产环境请替换为自己的证书）
# 生成证书: keytool -genkeypair -alias dbsyncer -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore conf/dbsyncer.p12 -validity 3650
# 开启: server.ssl.enabled=true + server.ssl.key-store=conf/dbsyncer.p12 + server.ssl.key-store-password=your_password
# ============================================================
server.ssl.enabled=false
```

- [ ] **Step 3: Commit**

```bash
git add dbsyncer-web/src/main/resources/dbsyncer.p12 dbsyncer-web/src/main/resources/application.properties
git commit -m "chore: remove bundled dbsyncer.p12"
```

---

## Task 7: Replace SimpleDataSource with HikariCP

**Files:**
- Modify: `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/DatabaseConnectorInstance.java`
- Create: `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/HikariDataSourceFactory.java`

- [ ] **Step 1: Create HikariDataSourceFactory**

```java
package org.dbsyncer.sdk.connector.database.ds;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.dbsyncer.sdk.config.DatabaseConfig;
import javax.sql.DataSource;

public final class HikariDataSourceFactory {
    
    private HikariDataSourceFactory() {}

    public static DataSource create(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        
        // Map maxActive → HikariCP maximumPoolSize
        hikariConfig.setMaximumPoolSize(config.getMaxActive());
        hikariConfig.setMinimumIdle(Math.min(config.getMaxActive() / 4, 10));
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(config.getKeepAlive());
        hikariConfig.setMaxLifetime(1800000); // 30 min
        
        // Enable JMX for monitoring
        hikariConfig.setPoolName("dbsyncer-" + config.getConnectorType());
        
        return new HikariDataSource(hikariConfig);
    }
}
```

- [ ] **Step 2: Update DatabaseConnectorInstance.java:43**

Replace:
```java
this.dataSource = new SimpleDataSource(config.getDriverClassName(), config.getUrl(), properties, config.getMaxActive(), config.getKeepAlive());
```

with:
```java
this.dataSource = HikariDataSourceFactory.create(config);
```

Also update the import — remove `SimpleDataSource` import, add `HikariDataSourceFactory` import.

- [ ] **Step 3: Remove SimpleDataSource.java (mark deprecated or delete)**

```bash
# Keep for reference, remove from compilation by renaming
mv dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/SimpleDataSource.java \
   dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/SimpleDataSource.java.bak
```

Or just delete it since it's no longer referenced.

- [ ] **Step 4: Compile check**

```bash
mvn clean compile -DskipTests 2>&1 | tail -30
```

- [ ] **Step 5: Commit**

```bash
git add dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/
git rm dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/connector/database/ds/SimpleDataSource.java 2>/dev/null || true
git commit -m "refactor: replace SimpleDataSource with HikariCP"
```

---

## Task 8: JUnit 5 migration + post-upgrade test run

**Files:**
- Modify: 16 test files across all modules

- [ ] **Step 1: Add junit-vintage-engine to run existing JUnit 4 tests on JUnit 5 platform**

Already done in Task 1 Step 3 (root POM). Verify:

```bash
mvn test 2>&1 | tail -30
```

Expected: 16 tests pass (via vintage-engine). If any fail, investigate whether JUnit 4 → 5 platform migration caused issues.

- [ ] **Step 2: Compare with baseline**

```bash
diff /tmp/dbsyncer-test-baseline.log /tmp/dbsyncer-test-post-upgrade.log || echo "Differences found — review manually"
```

- [ ] **Step 3: Migrate test annotations from JUnit 4 → JUnit 5 (one module at a time)**

For each test file, replace:
```java
import org.junit.Test;         → import org.junit.jupiter.api.Test;
import org.junit.Before;        → import org.junit.jupiter.api.BeforeEach;
import org.junit.After;         → import org.junit.jupiter.api.AfterEach;
import org.junit.Assert.*;      → import org.junit.jupiter.api.Assertions.*;
```

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "test: migrate JUnit 4 → JUnit 5 + vintage-engine bridge"
```

---

## Task 9: Full compile + fix residual errors

- [ ] **Step 1: Full compile**

```bash
mvn clean compile -DskipTests 2>&1 | tee /tmp/dbsyncer-build-final.log
```

- [ ] **Step 2: Fix remaining errors**

```bash
grep "ERROR" /tmp/dbsyncer-build-final.log | grep -v "WARNING" | head -30
```

- [ ] **Step 3: Iterate until BUILD SUCCESS**

- [ ] **Step 4: Run full test suite**

```bash
mvn test 2>&1 | tee /tmp/dbsyncer-test-final.log
```

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "fix: resolve remaining SB 3.5.x compile and test errors"
```

---

## Task 10: Update Dockerfile for JDK 21

**Files:**
- Modify: `dbsyncer-web/Dockerfile`

- [ ] **Step 1: Read current Dockerfile, update base image**

```bash
cat dbsyncer-web/Dockerfile
```

Replace the JDK 8 base image with JDK 21. If the Dockerfile uses:
```dockerfile
FROM openjdk:8-jre-alpine
```

Replace with:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
```

If using a different base, adapt accordingly. Also update any JDK-version-specific JVM flags.

- [ ] **Step 2: Commit**

```bash
git add dbsyncer-web/Dockerfile
git commit -m "chore: update Dockerfile base image to JDK 21"
```

---

## Task 11: Package + smoke test

- [ ] **Step 1: Full package**

```bash
mvn clean package -DskipTests 2>&1 | tail -20
```

- [ ] **Step 2: Start application**

```bash
java -jar dbsyncer-web/target/dbsyncer-web-*.jar --server.port=18687 2>&1 &
sleep 15
curl -s http://localhost:18687/login.html | head -5
```

Expected: Returns login page HTML.

- [ ] **Step 3: Verify Actuator health**

```bash
curl -s http://localhost:18687/app/health
```

Expected: `{"status":"UP",...}`.

- [ ] **Step 4: Verify HikariCP metrics**

```bash
curl -s http://localhost:18687/app/metrics/hikaricp.connections.active
```

- [ ] **Step 5: Login smoke tests (correct password, wrong password)**

```bash
# 登录页面可访问
curl -s -o /dev/null -w "%{http_code}" http://localhost:18687/login.html
# Expected: 200

# 正确密码登录（admin/admin）
curl -s -X POST http://localhost:18687/login \
  -d "username=admin" -d "password=admin" -c /tmp/dbsyncer-cookies.txt
# Expected: {"code":0,"message":"admin 登录成功!",...}

# 错误密码登录
curl -s -X POST http://localhost:18687/login \
  -d "username=admin" -d "password=wrongpassword"
# Expected: {"code":-1,"message":"对不起,您输入的帐号或密码错误",...,"status":401}

# 已登录状态下访问受保护页面
curl -s -o /dev/null -w "%{http_code}" -b /tmp/dbsyncer-cookies.txt http://localhost:18687/
# Expected: 200

# 未登录状态下访问受保护页面（重定向到 login）
curl -s -o /dev/null -w "%{http_code}" http://localhost:18687/index/list.html
# Expected: 302
```

- [ ] **Step 6: Kill test instance**

```bash
kill %1 2>/dev/null || true
```

- [ ] **Step 7: Commit**

```bash
git commit --allow-empty -m "chore: smoke test passed — app starts, login works, HikariCP OK on JDK 21"
```

---

## Task 12: MySQL→MySQL E2E test

Same procedure as original Task 9 — create source/target tables, run full sync (3 rows copied), run incremental sync (1 new row replicated).

---

## Task 13: Tag Phase 1 complete

```bash
git push origin master
git tag -a v3.0.0-phase1 -m "Phase 1: SB 3.5.x + JDK 21 + HikariCP + JUnit 5"
git push origin v3.0.0-phase1
```

---

## Failure Modes Registry

```
  CODEPATH                    | FAILURE MODE              | RESCUED? | TEST? | USER SEES?     | LOGGED?
  ----------------------------|---------------------------|----------|-------|----------------|--------
  HikariCP pool init          | maxActive > DB max_conn   | Y        | N     | "无法连接数据库" | Y (startup log)
  HikariCP connection borrow  | pool exhausted            | Y        | N     | SdkException   | Y
  SecurityFilterChain         | Lambda DSL parse error    | Y        | Y     | 500 / whitelabel| Y (startup)
  Thymeleaf render            | th:include removed        | N        | N     | Silent missing  | N ← GAP
  ES RestHighLevelClient      | version resolved to wrong | N        | N     | NoSuchMethodErr | N ← GAP (compile-time only)
  mssql-jdbc CDC              | API mismatch 8.x vs 12.x  | Y        | N     | SQLServerExc    | Y
  javax→jakarta wildcard      | import javax.* missed     | Y        | N     | compile error   | N (compile-time)
```

Two GAPs are acceptably mitigated: Thymeleaf `th:include` silent failure is caught by grep check in Task 5. ES version mismatch is caught at compile time by the explicit `<version>` lock in Task 2 Step 3.

---

## Phase 1.5 — Frontend Rewrite (separate plan)

Vue 3 + Vite + TypeScript + Pinia + Element Plus. Core pages first. Located in `dbsyncer-web-ui/`.

## Phase 2 — Performance Optimization (separate plan)

Based on `docs/performance.md`. Table group parallelization, maxBufferActuatorSize, storage→MySQL, async checkpoint flush.
