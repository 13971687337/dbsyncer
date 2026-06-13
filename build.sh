#!/bin/bash
# DBSyncer v3.x 构建脚本 (JDK 21 + Spring Boot 3.5)
# 要求: JDK 21+, Maven 3.6+

echo "JDK Version check:"
java -version

echo "Building DBSyncer..."
mvn clean -f pom.xml
mvn compile package -f pom.xml -Dmaven.test.skip=true

CURRENT_DIR=$(pwd);
mv $CURRENT_DIR/dbsyncer-web/target/dbsyncer-*.zip $CURRENT_DIR
echo "Build complete: $(ls dbsyncer-*.zip)"
