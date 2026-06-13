#!/bin/bash
# DBSyncer v3.x 安装构建脚本 (JDK 21 + Spring Boot 3.5)
# 要求: JDK 21+, Maven 3.6+

echo "JDK Version:"
java -version

echo "Installing DBSyncer modules to local Maven repository..."
mvn install -Dmaven.test.skip=true

echo "Install complete. Web UI build:"
echo "  cd dbsyncer-web-ui && npm install && npm run build"
