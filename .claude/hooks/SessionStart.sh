#!/bin/bash
# ============================================================
# SessionStart Hook — 会话启动时注入项目上下文
#
# 功能: 读取项目元信息，注入到 Claude 的 system prompt
# 触发: 每次新会话 / 上下文清空后恢复
# ============================================================

# 读取 stdin 中的事件数据
# {"session_id": "...", "cwd": "...", "tools": [...], "mcp_servers": [...]}

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# 检查关键文件是否存在，生成上下文摘要
CONTEXT=""

if [ -f "${PROJECT_DIR}/CLAUDE.md" ]; then
    CONTEXT="${CONTEXT}CLAUDE.md loaded\n"
fi

if [ -f "${PROJECT_DIR}/flow.md" ]; then
    CONTEXT="${CONTEXT}Superpowers workflow: brainstorm → plan → execute → verify → review → ship\n"
fi

if [ -f "${PROJECT_DIR}/backend/go.mod" ]; then
    GO_MODULE=$(head -1 "${PROJECT_DIR}/backend/go.mod" | sed 's/module //')
    CONTEXT="${CONTEXT}Go module: ${GO_MODULE}\n"
fi

if [ -f "${PROJECT_DIR}/frontend/package.json" ]; then
    VUE_VERSION=$(python3 -c "import json; d=json.load(open('${PROJECT_DIR}/frontend/package.json')); print(d.get('dependencies',{}).get('vue','unknown'))" 2>/dev/null || echo "3.x")
    CONTEXT="${CONTEXT}Frontend: Vue ${VUE_VERSION} + Element Plus\n"
fi

if [ -f "${PROJECT_DIR}/backend/data/hcetl.db" ]; then
    TABLE_COUNT=$(sqlite3 "${PROJECT_DIR}/backend/data/hcetl.db" "SELECT count(*) FROM sqlite_master WHERE type='table';" 2>/dev/null || echo "?")
    CONTEXT="${CONTEXT}SQLite DB: hcetl.db (${TABLE_COUNT} tables)\n"
fi

if [ -f "${PROJECT_DIR}/docs/2026-06-03-轻量级实时数仓设计.md" ]; then
    CONTEXT="${CONTEXT}Design doc: docs/2026-06-03-轻量级实时数仓设计.md\n"
fi

# 输出 JSON，将上下文注入到 additionalContext
cat << EOF
{
  "continue": true,
  "hookSpecificOutput": {
    "additionalContext": "Project: H-IVF+ Pro 数据中心 — ETL 管理平台\nTech: Go Gin + SQLite + Vue 3 + Element Plus + SeaTunnel Zeta\nDeploy: PG (无AVX2) 或 Doris (有AVX2)\n\n${CONTEXT}"
  }
}
EOF
