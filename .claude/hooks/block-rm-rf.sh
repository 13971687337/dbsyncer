#!/bin/bash

# block-rm-rf.sh - 阻止危险的 rm -rf 命令
# 放置在 .claude/hooks/ 目录下

# 读取从 stdin 传入的 JSON 输入
INPUT=$(cat)

# 提取命令内容（假设 Claude 发送的格式包含 command 字段）
COMMAND=$(echo "$INPUT" | jq -r '.command // empty')

# 如果没有 jq（或 jq 提取失败），用 grep/sed 提取
if [ -z "$COMMAND" ]; then
    COMMAND=$(echo "$INPUT" | grep -o '"command":"[^"]*"' | cut -d'"' -f4)
fi

# 定义危险模式
DANGEROUS_PATTERNS=(
    "rm -rf /"
    "rm -rf /*"
    "rm -rf ~"
    "rm -rf ."
    "rm -rf *"
    "rm -rf /home"
    "rm -rf /etc"
    "rm -rf /usr"
    "rm -rf /var"
    "rm -rf /bin"
    "rm -rf /boot"
    "rm -rf --no-preserve-root"
)

# 检查命令是否危险
is_dangerous() {
    local cmd="$1"

    # 完全匹配危险模式
    for pattern in "${DANGEROUS_PATTERNS[@]}"; do
        if [[ "$cmd" == "$pattern" ]] || [[ "$cmd" == "$pattern "* ]] || [[ "$cmd" == *" $pattern" ]]; then
            return 0
        fi
    done

    # 使用正则表达式检查
    if [[ "$cmd" =~ rm[[:space:]]+-rf[[:space:]]+/?$ ]] || \
       [[ "$cmd" =~ rm[[:space:]]+-rf[[:space:]]+/\* ]] || \
       [[ "$cmd" =~ rm[[:space:]]+-rf[space:]]+~ ]] || \
       [[ "$cmd" =~ rm[[:space:]]+-rf[[:space:]]+\.[[:space:]]*$ ]] || \
       [[ "$cmd" =~ rm[[:space:]]+-rf[[:space:]]+\* ]]; then
        return 0
    fi

    return 1
}

# 检查是否包含 --no-preserve-root
has_no_preserve_root() {
    [[ "$1" == *"--no-preserve-root"* ]]
}

# 主逻辑
if is_dangerous "$COMMAND" || has_no_preserve_root "$COMMAND"; then
    if [[ "$COMMAND" =~ \#danger-allow ]]; then
        exit 0
    fi
    # 输出阻止信息（JSON 格式，Claude 可识别）
    cat << EOF
{
    "action": "block",
    "message": "⚠️ 安全警告：检测到危险的 'rm -rf' 命令！\n\n命令: $COMMAND\n\n此命令可能导致系统文件被删除。\n如需执行，请手动确认或添加 '#danger-allow' 注释。",
    "suggestion": "建议使用 'rm -ri' 或指定具体文件路径。"
}
EOF
    exit 1  # 阻止执行
fi

# 检查是否过于宽松的 rm -rf
if [[ "$COMMAND" =~ rm[[:space:]]+-rf[[:space:]]+[^/][^/]*$ ]] && \
   [[ ! "$COMMAND" =~ \#danger-allow ]]; then
    # 对于删除当前目录下所有内容，发出警告但允许（根据需求可改为 block）
    cat << EOF
{
    "action": "warn",
    "message": "⚠️ 警告：即将执行 'rm -rf'，可能删除大量文件。\n命令: $COMMAND\n\n如需跳过检查，请在命令后添加 '#danger-allow'"
}
EOF
    exit 0  # 允许执行
fi

# 允许正常命令
exit 0