
# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:

- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:

- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## 5. 前端 API 调用规范（dbsyncer-web-ui）

**Vue 组件禁止直接调用 `request()`。** 所有 HTTP 请求必须通过 `src/api/` 下的对应模块函数发起。

```typescript
// ❌ 错误：Vue 组件中直接 import request 并调用
import request from '@/utils/request'
await request({ url: '/user/add', method: 'post', params: form })

// ✅ 正确：在 src/api/<module>.ts 中定义函数，Vue 组件 import 函数调用
import { addUser } from '@/api/user'
await addUser(form)
```

**规则：**
- `src/views/` 下的 `.vue` 文件不得出现 `import request from '@/utils/request'`
- `src/views/` 下的 `.vue` 文件不得出现 `request(` 调用
- 每个后端 API 路径（如 `/user/add`、`/connector/getConnectorTypeAll`）必须在 `src/api/` 对应模块中封装为导出函数
- API 函数命名：`get*` 查询、`add*` 新增、`edit*` 修改、`remove*` 删除、`search*` 分页搜索
- 检查命令：`grep -rn "import request\|request(" src/views/ --include="*.vue"` 必须返回空

## 语言规则

- 所有给用户的回复使用**简体中文**
- 代码注释使用**中文**
- 文档使用**中文**
- commit message 使用**中文**
- 变量名、函数名等代码标识符可使用英文
