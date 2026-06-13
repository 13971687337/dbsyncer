@echo off
REM DBSyncer v3.x 构建脚本 (JDK 21 + Spring Boot 3.5)
REM 要求: JDK 21+, Maven 3.6+

echo JDK Version check:
java -version

echo "Clean Project ..."
call mvn clean -f pom.xml

echo "Build Project ..."
call mvn compile package -f pom.xml -D"maven.test.skip=true"

set CP_PATH=%~dp0
move %CP_PATH%dbsyncer-web\target\dbsyncer-*.zip %CP_PATH%
echo Build complete.

:exit