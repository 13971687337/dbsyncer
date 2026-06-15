#!/bin/bash
# ============================================================
# PostToolUse Hook — 自动快速检查 + 代码审查提醒 + 模块文档同步
# ============================================================

INPUT=$(cat)
TOOL_NAME=$(echo "$INPUT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('tool_name',''))" 2>/dev/null)
FILE_PATH=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path',''))" 2>/dev/null)

PROJECT_DIR="${HOME}/DataCenterRespo/HIVFPRO/stat"
DOC_DIR="docs/modules"
WARNINGS=""

# ---- 1. 模块文档同步检查 ----
check_module_doc() {
  local file="$1"
  case "$file" in
    *datasource*)  echo "${DOC_DIR}/数据源管理.md" ;;
    *task*)        echo "${DOC_DIR}/任务管理.md" ;;
    *workflow*)    echo "${DOC_DIR}/工作流管理.md" ;;
    *Login*|*auth*) echo "${DOC_DIR}/登录与认证.md" ;;
    *setting*|*system*) echo "${DOC_DIR}/系统设置.md" ;;
    *tokens.css*|*AppLayout*|*main.ts*) echo "${DOC_DIR}/主题与布局.md" ;;
    *user*)        echo "${DOC_DIR}/系统设置.md" ;;
    *checkpoint*)  echo "${DOC_DIR}/系统设置.md" ;;
  esac
}

if [ "$TOOL_NAME" = "Write" ] || [ "$TOOL_NAME" = "Edit" ]; then
  DOC=$(check_module_doc "$FILE_PATH" 2>/dev/null)
  if [ -n "$DOC" ]; then
    WARNINGS="📝 ${FILE_PATH} → 请检查 ${DOC}"
  fi
fi

# ---- 2. Go 快速静态检查 ----
if [[ "$FILE_PATH" == *.go ]]; then
  # gofmt
  if command -v gofmt &>/dev/null; then
    UNFMT=$(gofmt -l "$FILE_PATH" 2>/dev/null)
    if [ -n "$UNFMT" ]; then
      WARNINGS="${WARNINGS}
⚠ gofmt: ${FILE_PATH} 需要格式化 (run: gofmt -w ${FILE_PATH})"
    fi
  fi
  # go vet — only on the file's package
  PKG_DIR=$(dirname "$FILE_PATH")
  if [ -d "$PKG_DIR" ]; then
    VET_OUT=$(cd "$PKG_DIR" && go vet . 2>&1)
    if [ -n "$VET_OUT" ]; then
      # Only show first 2 lines
      VET_SHORT=$(echo "$VET_OUT" | head -2)
      WARNINGS="${WARNINGS}
🔍 go vet: ${VET_SHORT}"
    fi
  fi
fi

# ---- 3. 代码审查提醒（编辑计数器）----
EDIT_COUNT_FILE="/tmp/hivfpro-edits-$$.txt"
if [ "$TOOL_NAME" = "Write" ] || [ "$TOOL_NAME" = "Edit" ]; then
  if [ -f "$EDIT_COUNT_FILE" ]; then
    COUNT=$(cat "$EDIT_COUNT_FILE")
    COUNT=$((COUNT + 1))
  else
    COUNT=1
  fi
  echo "$COUNT" > "$EDIT_COUNT_FILE"

  # 每 7 次编辑提醒一次轻量审查，每 20 次提醒深度审查
  if [ "$COUNT" -eq 7 ] || [ "$((COUNT % 20))" -eq 0 ]; then
    WARNINGS="${WARNINGS}
🧪 已编辑 ${COUNT} 次 — 建议运行 /code-review --effort medium"
  fi
fi

# Clean JSON output
echo "{\"continue\": true, \"hookSpecificOutput\": {\"additionalContext\": \"${WARNINGS}\"}}"
