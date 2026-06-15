#!/bin/bash
# ============================================================
# PreCompact Hook — 上下文压缩前保留关键信息
# 触发: 会话上下文即将被压缩时
# ============================================================

INPUT=$(cat)
MSG_COUNT=$(printf '%s' "$INPUT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message_count',0))" 2>/dev/null)

SUMMARY="会话压缩前状态 — 消息数: ${MSG_COUNT}\n"
SUMMARY="${SUMMARY}请保留以下关键上下文: 当前分支、未完成的 TodoWrite 任务、进行中的 TDD 测试状态\n"

cat << EOF
{"continue": true, "hookSpecificOutput": {"additionalContext": "${SUMMARY}"}}
EOF
