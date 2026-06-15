#!/bin/bash
# ============================================================
# SessionStart Hook — 会话启动时注入项目上下文
#
# 功能: 读取项目元信息，注入到 Claude 的 system prompt
# 触发: 每次新会话 / 上下文清空后恢复
# ============================================================



PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# 检查关键文件是否存在，生成上下文摘要
CONTEXT=""

if [ -f "${PROJECT_DIR}/CLAUDE.md" ]; then
    CONTEXT="${CONTEXT}CLAUDE.md loaded"$'\n'
fi

# 输出 JSON 格式结果
echo "{\"continue\": true, \"hookSpecificOutput\": {\"additionalContext\": \"${CONTEXT}\"}}"


