# 学生积分管理模块设计

## 1. 文档目标

本文档用于在当前单词学习系统中设计“学生积分管理模块”。模块目标是把学生在学习计划、错词复习、考试、自主学习、课堂活动中的有效行为沉淀为可追踪、可审计、可配置的积分体系，帮助教师做激励管理，也为后续排行榜、积分兑换、徽章成长等能力预留边界。

本文档只做模块设计，不直接修改代码。

## 2. 当前项目上下文

当前后端是 Spring Boot 3.2.0 + PostgreSQL + Flyway 的 Java 服务，已有下列相关能力：

- 用户与角色：`ADMIN`、`TEACHER`、`STUDENT`。
- 班级与师生关系：课堂、班级成员、教师学生关系已经存在。
- 学习计划：教师创建学习计划，学生完成每日任务，系统记录学习结果、完成率、专注度。
- 学生首页：`StudentDashboardController` 聚合学生当前学习任务，并通过 `/api/students/me/dashboard/records` 提交学习记录。
- 学生单词记忆：`StudentWordMemoryService` 已经维护全局记忆、错词、收藏，并接收计划学习结果。
- 考试模块：考试有分数、正确数、提交记录。

积分模块应作为独立激励层存在。它消费学习、考试、教师调整等业务事件，但不替代学习计划、考试分数或单词记忆本身。

## 3. 设计目标

### 3.1 业务目标

- 学生完成有效学习行为后自动获得积分。
- 学生可以查看自己的积分余额、累计获得、消耗记录和近期明细。
- 教师可以查看自己管理学生的积分概况，并进行有限的人工加减分。
- 管理员可以配置积分规则、处理异常流水、查看全局积分审计。
- 积分发放需要幂等，避免网络重试或重复提交导致重复加分。
- 积分流水需要不可篡改，余额由流水聚合或受控汇总字段维护。

### 3.2 教学目标

- 鼓励持续学习，而不是单次刷量。
- 奖励高质量完成，例如完成任务、正确率、连续学习、错词复习清除。
- 避免单纯按点击次数给分，减少刷分空间。
- 教师能用积分发现活跃学生和需要激励的学生。

### 3.3 非目标

- 首期不做复杂积分商城支付闭环。
- 首期不做跨学校、多租户积分隔离。
- 首期不把积分直接等同于考试成绩。
- 首期不做复杂反作弊模型、次数限制和额度限制，只做基础幂等与账本一致性。
- 首期不允许学生之间转赠积分。

## 4. 推荐方案与取舍

### 4.1 方案 A：在学习计划表上直接增加积分字段

优点：

- 实现最少，能快速给每日任务加分。
- 查询学习计划积分比较直接。

缺点：

- 积分来源会被学习计划绑定，考试、错词、教师手动调整难以纳入。
- 缺少独立流水，不利于审计和撤销。
- 后续做兑换、排行榜时会牵扯学习计划服务。

结论：不推荐。

### 4.2 方案 B：独立积分账户 + 积分事件 + 积分流水 + 规则服务

优点：

- 与学习、考试、课堂等模块解耦。
- 所有积分变化都有流水，便于审计、回放、纠错。
- 积分事件可以承接主业务完成后的补发、失败重试和管理员手动重试审计。
- 支持自动发放、人工调整、兑换冻结、撤销等多种场景。
- 更适合后续扩展排行榜、商城、徽章、活动任务。

缺点：

- 首期需要新增多张表和一个服务边界。
- 业务事件接入时需要设计幂等键。

结论：推荐。该方案最符合当前项目已经形成的 service/controller/model/repository 分层。

### 4.3 方案 C：事件总线式积分中心

优点：

- 业务模块只发布事件，积分中心异步消费，扩展性最好。
- 适合高并发和多系统接入。

缺点：

- 当前项目还没有消息队列基础设施。
- 首期引入异步一致性会增加排查成本。

结论：作为远期演进方向，首期不采用。

## 5. 模块边界

积分模块建议命名为 `StudentPoint` 或 `Point`，包含以下职责：

- 积分账户：维护学生可用积分、冻结积分、累计获得、累计消耗。
- 积分流水：记录每一次积分增加、扣减、冻结、解冻、撤销。
- 积分事件：记录学习、考试、视频等主业务完成后“应该发积分”的待处理任务，并支持失败重试。
- 积分规则：定义不同业务事件如何换算积分。
- 幂等控制：确保同一业务事件只入账一次。
- 人工调整：教师或管理员在权限范围内加减分并记录原因。
- 查询统计：提供学生、教师、管理员所需的积分列表和概览。

不属于积分模块的职责：

- 判断学生任务是否完成：由学习计划模块负责。
- 判断考试是否有效：由考试模块负责。
- 判断单词是否进入错词本：由学生单词记忆模块负责。
- 班级成员管理：由课堂模块负责。

## 6. 核心概念

### 6.1 积分账户

每个学生必须有且最多有一个积分账户。学生创建成功时必须同步创建积分账户，积分入账流程不负责补建账户。账户保存高频查询所需的汇总值：

- `availablePoints`：可用积分。
- `frozenPoints`：冻结积分，用于后续兑换确认。
- `lifetimeEarnedPoints`：历史累计获得积分。
- `lifetimeSpentPoints`：历史累计消耗积分。
- `status`：账户状态，例如 `ACTIVE`、`FROZEN`。

账户余额只能由积分服务通过流水变更，不允许业务模块直接修改。
如果历史学生缺少积分账户，应通过一次性数据迁移补齐，而不是在积分入账时隐式创建。

### 6.2 积分流水

积分流水是积分系统的事实来源。每次积分变化都写一条记录：

- 自动奖励：完成学习任务、考试达标、连续打卡。
- 人工调整：教师奖励、教师扣减、管理员修正。
- 消耗兑换：兑换奖励、取消兑换、兑换失败回滚。
- 撤销冲正：对错误流水做反向流水，不物理删除原流水。

流水必须包含 `studentId`、`amount`、`balanceAfter`、`transactionType`、`sourceType`、`sourceId`、`idempotencyKey`、`operatorId`、`reason`。

### 6.3 积分事件

积分事件用于记录“某个业务行为命中某条积分规则，应该尝试发放一次积分”。它是主业务和积分入账之间的缓冲层，适用于视频观看、学习任务、考试提交等耗时或不可回滚的学习行为。

- `student_point_events` 记录待处理、处理中、处理成功或处理失败的积分发放任务。
- `student_point_transactions` 只记录已经成功改变账户余额的积分流水。
- `student_point_events` 以“积分规则发放”为粒度，而不是以原始业务行为为粒度。
- MVP 明确只有一个后台积分事件处理服务实例。后台自动重试不按多 worker、多实例并发领取模型设计。
- 同一业务行为如果命中多条积分规则，应创建多条积分事件。
- 每条积分事件最多生成一条积分流水。
- 积分事件创建时应固化本次发放分值，后续重试使用事件中的分值快照，不按最新规则重新计算。
- 规则编码 `rule_code` 是积分规则的稳定业务标识，必须全局唯一，创建后禁止修改。
- MVP 不设计规则版本号。规则修改或停用前，系统必须检查该规则是否存在未完成积分事件。
- 如果存在 `PENDING`、`PROCESSING`、`FAILED` 状态的事件，则拒绝修改或停用规则，并提示管理员先等待处理完成、手动重试或取消事件。
- 只有当该规则相关事件全部为 `SUCCEEDED` 或 `CANCELLED` 后，才允许修改或停用规则。
- 修改后的规则只影响后续新创建的积分事件。
- 积分计算失败时，主业务完成状态不回滚，事件保留为失败或待重试状态。
- 后台任务可以根据事件状态和重试次数继续补发积分。

例如学生观看 40 分钟视频并达到完成条件后，视频学习计划应立即判定完成；如果积分计算失败，只影响积分事件状态，不应把已完成的视频学习判定为失败。

### 6.4 积分规则

积分规则定义“什么行为给多少分”。建议首期使用数据库配置，后台接口可后续再做：

- 规则编码：`DAILY_TASK_COMPLETED`、`STUDY_RECORD_CORRECT`、`VIDEO_WATCH_COMPLETED`、`EXAM_SUBMITTED`。
- 基础积分：固定分值。
- 生效状态：启用、停用。
- 适用范围：全局、班级、学习计划、考试。
- 首期不设置次数限制、每日额度限制或每来源额度限制；后续如需要防刷、限额或频控，再按新需求单独设计。

### 6.5 幂等键

幂等键用于防止重复入账。建议格式：

- 每日任务完成：`study-day-task:{studyDayTaskId}:completed`
- 单词学习记录：`study-record:{studyRecordId}:{result}`
- 视频观看完成：`video-watch:{studentStudyPlanId}:{videoId}:completed`
- 考试提交：`exam:{examId}:submitted`
- 教师人工调整：`manual-adjustment:{adjustmentRequestId}`

数据库对 `idempotency_key` 建唯一索引。

`source_key` 和 `idempotency_key` 需要区分：

- `source_id` 表示单一业务主键，用于稳定回写来源业务表。
- `source_key` 表示原始业务来源，可被多条积分事件共享。
- `idempotency_key` 表示某条积分规则发放的唯一键，必须包含 `source_key` 和 `rule_code`。

例如考试 `examId=100` 且分数优秀：

- `source_key = exam:100:submitted`
- `idempotency_key = exam:100:submitted:EXAM_SUBMITTED`
- `idempotency_key = exam:100:submitted:EXAM_SCORE_EXCELLENT`

创建积分事件时必须按 `idempotency_key` 做幂等处理：

- 如果同一个 `idempotency_key` 的积分事件已经存在，不再创建新事件。
- 已有事件为 `PENDING`、`PROCESSING` 或 `FAILED` 时，返回已有事件，主业务完成状态不回滚，前端可展示“积分处理中”或“积分稍后到账”。
- 已有事件为 `SUCCEEDED` 时，返回已有事件及其关联流水或当前账户结果。
- 已有事件为 `CANCELLED` 时，不重新发放积分，返回已有取消结果或不展示积分到账提示。
- 唯一键冲突不能向外暴露为 500；服务层应查询并返回已有事件语义。

## 7. 积分来源设计

### 7.1 学习计划积分

建议首期接入以下事件：

| 事件 | 触发条件 | 默认积分 | 说明 |
| --- | --- | ---: | --- |
| 完成今日任务 | `StudyDayTask` 从非完成变为 `COMPLETED` | +10 | 由任务状态变化和幂等键防重复 |
| 单词答对 | 学习记录结果为 `CORRECT` | +1 | 首期不设置每日上限 |
| 错词复习答对 | 来源为复习且结果为 `CORRECT` | +2 | 强化错词修复 |
| 连续学习 3 天 | 学生连续完成任务达到 3 天 | +10 | 每达成一次里程碑发放 |
| 连续学习 7 天 | 学生连续完成任务达到 7 天 | +30 | 与 3 天奖励可叠加或按规则配置 |

注意：单词答对积分不应成为主要积分来源，否则学生可能通过短任务刷分。每日任务完成和连续学习应占更高权重。

### 7.2 考试积分

建议首期接入考试提交事件：

| 事件 | 触发条件 | 默认积分 | 说明 |
| --- | --- | ---: | --- |
| 提交考试 | 考试状态变为 `SUBMITTED` | +5 | 鼓励参与 |
| 考试及格 | 分数大于等于 60 | +10 | 与提交奖励可叠加 |
| 考试优秀 | 分数大于等于 90 | +20 | 与及格奖励二选一或按规则配置 |

考试积分使用 `examId` 做幂等，不因重复查看结果再次发放。

### 7.3 视频观看积分

建议首期如接入视频学习任务，需要把视频完成和积分发放解耦：

| 事件 | 触发条件 | 默认积分 | 说明 |
| --- | --- | ---: | --- |
| 视频观看完成 | 有效观看时长达到任务要求，或观看进度达到完成阈值 | +10 | 视频任务完成后创建积分事件，积分失败不回滚观看完成状态 |

视频观看积分使用 `studentStudyPlanId + videoId` 生成幂等键。对于 40 分钟以上的长视频，学生一旦满足完成条件，学习完成状态应立即保留；积分失败时通过 `student_point_events` 后台补发。

### 7.4 教师人工积分

教师可对自己管理的学生进行人工加减分：

- 课堂表现奖励。
- 作业补交奖励。
- 纪律或刷分扣减。
- 特殊活动奖励。

约束建议：

- 教师只能调整自己管理的学生。
- 首期不设置单次调整额度限制和每日调整额度限制。
- 必须填写原因。
- 管理员可查看和撤销教师调整。

### 7.5 管理员修正积分

管理员用于处理系统异常、数据迁移、活动补偿：

- 可对任意学生调整。
- 调整范围更大，但必须记录原因。
- 可发起冲正流水撤销错误积分。

## 8. 数据模型设计

### 8.1 `student_point_accounts`

作用：保存学生积分账户汇总。

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_id BIGINT NOT NULL`
- `available_points INT NOT NULL DEFAULT 0`
- `frozen_points INT NOT NULL DEFAULT 0`
- `lifetime_earned_points INT NOT NULL DEFAULT 0`
- `lifetime_spent_points INT NOT NULL DEFAULT 0`
- `status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

约束和索引：

- `UNIQUE (student_id)`
- `FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT`
- `CHECK (available_points >= 0)`
- `CHECK (frozen_points >= 0)`

### 8.2 `student_point_transactions`

作用：记录所有积分变化。

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `account_id BIGINT NOT NULL`
- `student_id BIGINT NOT NULL`
- `transaction_type VARCHAR(32) NOT NULL`
- `amount INT NOT NULL`
- `balance_before INT NOT NULL`
- `balance_after INT NOT NULL`
- `frozen_before INT NOT NULL DEFAULT 0`
- `frozen_after INT NOT NULL DEFAULT 0`
- `source_type VARCHAR(64) NOT NULL`
- `source_id BIGINT`
- `source_key VARCHAR(200)`
- `rule_code VARCHAR(64)`
- `idempotency_key VARCHAR(160) NOT NULL`
- `operator_id BIGINT`
- `operator_role VARCHAR(32)`
- `reason VARCHAR(500)`
- `reversed_transaction_id BIGINT`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

约束和索引：

- `UNIQUE (idempotency_key)`
- `INDEX (student_id, created_at DESC)`
- `INDEX (source_type, source_id)`
- `INDEX (source_key)`
- `INDEX (operator_id, created_at DESC)`
- `CHECK (amount <> 0)`

推荐枚举：

- `transaction_type`：`EARN`、`DEDUCT`、`FREEZE`、`UNFREEZE`、`SPEND`、`REVERSE`
- `source_type`：`STUDY_TASK`、`STUDY_RECORD`、`VIDEO_WATCH`、`EXAM`、`MANUAL_ADJUSTMENT`、`ADMIN_CORRECTION`、`REDEMPTION`

字段边界：

- `student_point_transactions` 是积分审计快照表，不建立数据库外键。
- `account_id`、`student_id`、`source_id`、`operator_id`、`reversed_transaction_id` 都保存写入流水时的历史 ID 快照。
- 流水写入后不因账户、用户、来源业务数据变化而级联更新或删除。
- `account_id` 和 `student_id` 的一致性由 `StudentPointService` 在写入流水前保证，不依赖数据库外键。

### 8.3 `student_point_events`

作用：记录待处理或处理失败的积分发放事件。事件表不代表积分已经到账，只负责排队、失败记录、重试和排查。

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_id BIGINT NOT NULL`
- `source_type VARCHAR(64) NOT NULL`
- `source_id BIGINT`
- `source_key VARCHAR(200)`
- `rule_code VARCHAR(64) NOT NULL`
- `rule_name VARCHAR(100)`
- `points INT NOT NULL`
- `idempotency_key VARCHAR(160) NOT NULL`
- `status VARCHAR(32) NOT NULL DEFAULT 'PENDING'`
- `auto_attempt_count INT NOT NULL DEFAULT 0`
- `next_retry_at TIMESTAMP`
- `processing_started_at TIMESTAMP`
- `last_error VARCHAR(1000)`
- `operator_id BIGINT`
- `operator_role VARCHAR(32)`
- `reason VARCHAR(500)`
- `transaction_id BIGINT`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `processed_at TIMESTAMP`

约束和索引：

- `UNIQUE (idempotency_key)`
- `INDEX (status, next_retry_at)`
- `INDEX (student_id, created_at DESC)`
- `INDEX (source_type, source_id)`
- `INDEX (source_key)`
- `FOREIGN KEY (transaction_id) REFERENCES student_point_transactions(id)`

推荐枚举：

- `status`：`PENDING`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`CANCELLED`

字段边界：

- `student_point_events` 存“有没有处理、失败原因、重试次数”。
- `student_point_transactions` 存“积分真的变了多少、余额前后是多少”。
- `source_id` 适合单一业务主键，`source_key` 适合多个字段组合出来的业务来源。
- `MANUAL_ADJUSTMENT` 事件必须设置 `source_id = adjustmentRequestId`，用于稳定回写 `student_point_adjustment_requests`。
- `source_key` 可被多条积分事件共享，`idempotency_key` 必须唯一。
- `points` 是事件创建时固化的发放分值，重试时不按最新规则重新计算。
- MVP 不使用 `rule_version`。规则修改和停用通过“未完成事件检查”来避免新旧规则混杂。
- `processing_started_at` 记录事件进入 `PROCESSING` 的时间，用于服务重启恢复和处理中超时恢复。
- 成功处理事件后，写入积分流水，并把 `transaction_id` 指向成功生成的流水。
- `CANCELLED` 只适用于尚未成功入账的积分事件。已成功事件不得取消；如需纠错，应对生成的积分流水做 `REVERSE` 冲正。

### 8.4 `student_point_event_attempts`

作用：记录积分事件的每一次处理尝试，包括自动重试和管理员手动重试。表名使用 `student_point_` 前缀，避免与其他业务事件或处理日志混淆。

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `event_id BIGINT NOT NULL`
- `attempt_no INT NOT NULL`
- `trigger_type VARCHAR(32) NOT NULL`
- `status VARCHAR(32) NOT NULL`
- `operator_id BIGINT`
- `operator_role VARCHAR(32)`
- `reason VARCHAR(500)`
- `error_message VARCHAR(1000)`
- `started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `finished_at TIMESTAMP`

约束和索引：

- `FOREIGN KEY (event_id) REFERENCES student_point_events(id)`
- `UNIQUE (event_id, attempt_no)`
- `INDEX (event_id, started_at DESC)`
- `INDEX (trigger_type, started_at DESC)`

推荐枚举：

- `trigger_type`：`AUTO`、`MANUAL`
- `status`：`SUCCEEDED`、`FAILED`

处理规则：

- `student_point_event_attempts` 只记录已经完成的一次处理尝试，不表达待处理或处理中状态。
- 正在处理状态由 `student_point_events.status = PROCESSING` 表达。
- `attempt_no` 是同一 `event_id` 下所有处理尝试的全局递增序号，自动处理和管理员手动重试共用一套序号。
- 写入尝试记录前，`attempt_no` 应取该 `event_id` 当前最大 `attempt_no + 1`；例如 3 次自动失败后，第 1 次管理员手动重试应记录为 `attempt_no = 4`。
- `auto_attempt_count` 只统计自动处理失败次数，用于控制自动重试最多 3 次，不用于生成 `attempt_no`。
- 自动处理和自动重试都记录 `trigger_type = AUTO`。
- 管理员手动重试记录 `trigger_type = MANUAL`，并必须记录 `operator_id`、`operator_role` 和 `reason`。
- 自动重试最多 3 次，管理员手动重试允许突破 3 次限制，但必须留下审计记录。

### 8.5 `student_point_rules`

作用：配置自动积分规则。

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `code VARCHAR(64) NOT NULL`
- `name VARCHAR(100) NOT NULL`
- `description VARCHAR(500)`
- `source_type VARCHAR(64) NOT NULL`
- `base_points INT NOT NULL`
- `scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL'`
- `scope_id BIGINT`
- `enabled BOOLEAN NOT NULL DEFAULT TRUE`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

约束：

- `UNIQUE (code)`

字段边界：

- `code` 对应事件和流水中的 `rule_code`，作为规则稳定业务标识。
- `code` 必须全局唯一，创建后禁止修改；管理员修改规则时不得修改 `code`。
- 规则修改或停用前，使用 `code` 检查是否存在 `PENDING`、`PROCESSING`、`FAILED` 状态的积分事件。
- 首期规则只表达“是否命中”和“固定分值”，不表达次数限制、每日额度限制、每周额度限制或每来源额度限制。

### 8.6 `student_point_adjustment_requests`

作用：记录教师或管理员的人工调整请求。

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_id BIGINT NOT NULL`
- `amount INT NOT NULL`
- `reason VARCHAR(500) NOT NULL`
- `requested_by BIGINT NOT NULL`
- `requested_role VARCHAR(32) NOT NULL`
- `status VARCHAR(32) NOT NULL DEFAULT 'PENDING'`
- `transaction_id BIGINT`
- `reverse_transaction_id BIGINT`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `processed_at TIMESTAMP`
- `reversed_at TIMESTAMP`

首期采用“提交后立即尝试入账”，但调整请求本身需要完整表达待处理、成功、失败和撤销状态：

- `PENDING`：已提交，待积分事件处理或正在处理。
- `APPLIED`
- `FAILED`：积分事件处理失败，需要后台重试、管理员手动处理或重新提交。
- `REJECTED`：后续审批模式下拒绝。
- `REVERSED`：已通过流水冲正撤销；`transaction_id` 保留原入账流水，`reverse_transaction_id` 记录冲正流水。

### 8.7 `student_point_redemptions`

作用：为后续积分兑换预留。

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `student_id BIGINT NOT NULL`
- `item_name VARCHAR(120) NOT NULL`
- `points INT NOT NULL`
- `status VARCHAR(32) NOT NULL`
- `freeze_transaction_id BIGINT`
- `spend_transaction_id BIGINT`
- `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
- `completed_at TIMESTAMP`
- `cancelled_at TIMESTAMP`

首期如果不做商城，可以只在文档中保留，不进入 MVP。

## 9. 后端模块划分

建议新增以下 Java 包内对象。

### 9.1 model

- `StudentPointAccount`
- `StudentPointTransaction`
- `StudentPointEvent`
- `StudentPointEventAttempt`
- `StudentPointRule`
- `StudentPointAdjustmentRequest`
- `PointEventStatus`
- `PointEventAttemptTriggerType`
- `PointEventAttemptStatus`
- `PointTransactionType`
- `PointSourceType`
- `PointAccountStatus`
- `PointAdjustmentStatus`

### 9.2 repository

- `StudentPointAccountRepository`
- `StudentPointTransactionRepository`
- `StudentPointEventRepository`
- `StudentPointEventAttemptRepository`
- `StudentPointRuleRepository`
- `StudentPointAdjustmentRequestRepository`

### 9.3 service

- `StudentPointService`：积分入账、扣减、查询、幂等。
- `StudentPointEventService`：按规则创建积分事件、同步尝试处理、失败记录、后台重试和手动重试审计。
- `StudentPointRuleService`：规则查找、规则计算和规则启停判断。
- `StudentPointAdjustmentService`：教师和管理员人工调整。
- `StudentPointRankingService`：排行榜查询，首期可选。

### 9.4 controller

- `StudentPointController`：学生查看自己的积分。
- `TeacherStudentPointController`：教师查看和调整学生积分。
- `AdminPointController`：管理员规则、审计、修正。

## 10. API 设计

### 10.1 学生端接口

学生接口放在 `/api/students/me/points` 下。

#### 获取我的积分概况

`GET /api/students/me/points`

响应示例：

```json
{
  "availablePoints": 126,
  "frozenPoints": 0,
  "lifetimeEarnedPoints": 180,
  "lifetimeSpentPoints": 54,
  "todayEarnedPoints": 18,
  "currentStreak": 5,
  "levelName": "坚持新星"
}
```

#### 获取我的积分明细

`GET /api/students/me/points/transactions?page=0&size=20`

响应示例：

```json
{
  "items": [
    {
      "id": 501,
      "transactionType": "EARN",
      "sourceType": "STUDY_TASK",
      "amount": 10,
      "balanceAfter": 126,
      "reason": "完成今日学习任务",
      "createdAt": "2026-07-15T20:30:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

#### 获取我的积分排行榜位置

`GET /api/students/me/points/rank`

首期可选。如果实现，默认只返回学生所在班级范围内排名，避免跨班级比较。

### 10.2 教师端接口

教师接口统一放在 `/api/teachers/me/points` 下。

#### 查看受管学生积分概况

`GET /api/teachers/me/points/students?page=0&size=20&name=关键词`

响应内容：

- 学生 ID、姓名。
- 可用积分。
- 今日获得积分。

`GET /api/teachers/me/points/students/{studentId}` 返回单个受管学生账户概况。

#### 查看学生积分明细

`GET /api/teachers/me/points/students/{studentId}/transactions?page=0&size=20`

教师只能查看自己管理学生的数据。

#### 人工调整积分

`POST /api/teachers/me/points/students/{studentId}/adjustments`

请求示例：

```json
{
  "requestKey": "由前端为本次提交生成的稳定键",
  "amount": 20,
  "reason": "课堂积极回答问题",
  "replacesAdjustmentRequestId": null
}
```

响应示例：

```json
{
  "adjustmentId": 88,
  "transactionId": 9001,
  "studentId": 120,
  "amount": 20,
  "availablePoints": 146,
  "status": "APPLIED"
}
```

### 10.3 管理员接口

管理员接口放在 `/api/admin/points` 下。

- `GET /api/admin/points/rules`
- `POST /api/admin/points/rules`
- `PUT /api/admin/points/rules/{id}`
- `GET /api/admin/points/rules/{id}/audits`
- `GET /api/admin/points/accounts`
- `GET /api/admin/points/transactions`
- `GET /api/admin/points/events?status=FAILED`
- `GET /api/admin/points/events/{id}/attempts`
- `POST /api/admin/points/events/{id}/retry`
- `POST /api/admin/points/events/{id}/cancel`
- `POST /api/admin/points/students/{studentId}/adjustments`
- `POST /api/admin/points/transactions/{id}/reverse`

规则创建和更新请求必须填写 `reason`。系统将操作人、原因和规则变更前后快照写入 `student_point_rule_audits`，规则编码仍不可修改。

## 11. 核心业务流程

### 11.1 自动发放学习积分

1. 学生提交学习记录。
2. `StudyPlanService` 完成原有学习记录、任务状态、单词记忆更新。
3. 学习模块在状态真正变化后，根据命中的积分规则创建一条或多条 `student_point_events`。
4. 主业务提交成功后，积分事件可以被同步尝试处理，也可以由后台任务稍后处理。
5. 如果主业务完成状态已经保存成功，但积分事件创建失败，主业务不回滚；系统应记录错误日志、`source_type`、`source_id` 或 `source_key`，后续由管理员或补偿脚本补建积分事件。
6. 积分事件创建时固化 `source_key`、`rule_code`、`points` 和 `idempotency_key`。事件处理时使用事件中的 `points` 快照，不按最新规则重新计算分值。
7. 积分服务锁定学生积分账户；账户必须已在学生创建时同步创建。
8. 积分服务在同一事务中写入流水并更新账户余额。
9. 处理成功后，事件状态变为 `SUCCEEDED`，并记录 `transaction_id`。
10. 如果积分处理失败，学习记录和任务状态不回滚，事件状态变为 `FAILED` 或保持待重试状态，记录 `last_error`、`auto_attempt_count` 和 `next_retry_at`。
11. 返回更新后的学生首页；如果积分暂未到账，可由前端展示“积分稍后到账”或不展示即时积分提示。

### 11.2 自动发放每日任务完成积分

1. 学习记录提交后，`StudyDayTask` 可能从 `IN_PROGRESS` 变为 `COMPLETED`。
2. 只有状态首次变为 `COMPLETED` 时触发积分。
3. 创建积分事件，`source_key` 使用 `study-day-task:{studyDayTaskId}:completed`，`idempotency_key` 使用 `{sourceKey}:DAILY_TASK_COMPLETED`。
4. 如果任务之前已经完成过，不重复创建事件，也不重复发放。
5. 积分事件处理失败不影响 `StudyDayTask` 的完成状态。

### 11.3 考试积分

1. 学生提交考试。
2. `ExamService` 计算分数并保存考试状态。
3. 考试状态首次变为 `SUBMITTED` 后，按命中的规则分别创建积分事件。
4. 例如分数 92 时，可以创建 `EXAM_SUBMITTED` 和 `EXAM_SCORE_EXCELLENT` 两条事件。
5. 两条事件可以共享 `source_key = exam:{examId}:submitted`，但 `idempotency_key` 必须分别包含各自的 `rule_code`。
6. 同一 `examId` 只创建一次提交积分事件和一次成绩档位积分事件。
7. 积分处理失败不影响考试提交结果，后续通过事件重试补发。

### 11.4 教师人工调整

1. 教师提交调整请求。
2. 系统校验教师是否管理该学生。
3. 系统校验调整金额非 0、原因非空。
4. 创建 `student_point_adjustment_requests`，初始状态为 `PENDING`。
5. 创建 `MANUAL_ADJUSTMENT` 积分事件，`source_type = MANUAL_ADJUSTMENT`，`source_id = adjustmentRequestId`，`source_key` 使用 `manual-adjustment:{adjustmentRequestId}`，`idempotency_key` 使用 `{sourceKey}:MANUAL_ADJUSTMENT`。
6. 对人工调整，建议同步处理积分事件。
7. 处理成功后，通过事件 `source_id` 找到对应 `student_point_adjustment_requests`，将调整请求状态更新为 `APPLIED`，记录 `transaction_id`，并返回调整后的账户余额。
8. 如果积分事件处理失败，通过事件 `source_id` 找到对应调整请求并更新为 `FAILED`，但原积分事件仍保留并进入自动重试流程。
9. `MANUAL_ADJUSTMENT` 事件处理成功时，流水、账户余额、事件 `SUCCEEDED` 状态、事件 `transaction_id`、调整请求 `APPLIED` 状态、调整请求 `transaction_id` 和事件尝试记录必须在同一事务中提交。
10. `MANUAL_ADJUSTMENT` 事件处理失败时，事件 `FAILED` 状态、失败原因、事件尝试记录和调整请求 `FAILED` 状态必须在同一事务中提交。
11. 如果 `MANUAL_ADJUSTMENT` 事件处理成功，但无法通过 `source_id` 更新对应调整请求，则本次积分入账事务必须整体回滚。
12. 原 `MANUAL_ADJUSTMENT` 事件仍处于 `PENDING`、`PROCESSING`，或处于 `FAILED` 且 `auto_attempt_count < 3` 时，表示自动重试尚未完成；此时不允许教师重新提交一笔替代性人工调整，避免先手工调整成功、后续自动重试又成功导致重复加减分。
13. 原 `MANUAL_ADJUSTMENT` 事件自动重试成功后，必须通过事件 `source_id` 将对应调整请求从 `FAILED` 更新为 `APPLIED`，并写入 `transaction_id`。
14. 原 `MANUAL_ADJUSTMENT` 事件自动重试达到 3 次后仍为 `FAILED` 时，才允许教师重新提交替代性人工调整；重新提交前必须先将旧事件置为 `CANCELLED`，并在旧事件 `reason` 中记录被新调整请求替代，避免旧事件后续再被管理员手动重试。
15. 如果管理员选择手动重试旧人工调整事件，则在重试前必须确认该事件没有被新的替代调整请求取消或替代。

### 11.5 视频观看完成积分

1. 学生观看视频，系统记录播放进度。
2. 当视频观看满足完成条件，例如有效播放时间达到要求后，学习计划或视频模块判定该视频任务完成。
3. 视频任务完成状态先保存，不因积分计算失败而回滚。
4. 系统创建积分事件，`source_key` 建议使用 `video-watch:{studentStudyPlanId}:{videoId}:completed`。
5. `idempotency_key` 建议使用 `{sourceKey}:VIDEO_WATCH_COMPLETED`。
6. 积分事件处理成功后写入积分流水；处理失败时记录错误并等待后台重试。

### 11.6 积分事件重试和取消

MVP 部署约束：积分事件后台处理服务只有一个实例。自动重试任务由该单一后台服务执行，不需要为多后台实例设计事件抢占、分片或分布式锁。

1. 自动任务只扫描 `PENDING` 或 `FAILED` 且 `auto_attempt_count < 3` 的积分事件。
2. 自动重试和管理员手动重试在处理前，都必须先检查事件状态。
3. 只有 `PENDING` 或 `FAILED` 状态的事件可以进入 `PROCESSING`。
4. 事件进入 `PROCESSING` 后，其他处理入口不得重复处理该事件。
5. 如果管理员手动重试遇到 `PROCESSING` 状态，返回 409，提示“事件正在处理中，请稍后再试”。
6. 每次自动处理失败后，写入 `student_point_event_attempts`，更新 `auto_attempt_count`、`last_error` 和 `next_retry_at`。
7. 自动重试最多 3 次。达到 3 次后保留 `FAILED` 状态，不再自动重试。
8. 管理员可以手动重试失败事件，手动重试不受 3 次自动重试限制。
9. 每次管理员手动重试必须写入 `student_point_event_attempts`，记录操作人、角色、原因、开始时间、结束时间和结果；管理员手动重试不增加 `auto_attempt_count`。
10. 写入 `student_point_event_attempts` 和更新事件最终状态必须在同一事务中完成，避免事件状态变化但审计记录缺失。
11. 服务启动时，单一后台积分事件处理服务应把重启前残留的 `PROCESSING` 事件恢复为 `FAILED`，记录 `last_error = 'PROCESSING_INTERRUPTED_BY_SERVER_RESTART'`，并设置 `next_retry_at = CURRENT_TIMESTAMP`。
12. 定时任务可以把 `processing_started_at` 超过 10 分钟且仍为 `PROCESSING` 的事件恢复为 `FAILED`，记录 `last_error = 'PROCESSING_TIMEOUT'`，避免事件永久卡死。
13. 未成功入账的事件可以由管理员取消，状态变为 `CANCELLED`。
14. 已经 `SUCCEEDED` 的事件不能取消；如果需要纠错，应对生成的积分流水做 `REVERSE` 冲正。

### 11.7 流水撤销

1. 管理员选择一条可撤销流水。
2. 系统检查该流水未被撤销过。
3. 系统锁定对应积分账户，避免冲正校验与其他积分变更交错。
4. 如果原流水为正向加分，冲正需要扣回相同积分；当前可用积分不足时，拒绝整笔冲正，返回 409 和错误码 `INSUFFICIENT_POINTS_FOR_REVERSAL`，不创建冲正流水，也不修改人工调整请求。
5. 首期不允许部分冲正，也不允许冲正后账户余额为负数；管理员可在学生后续积分足够时重新发起整笔冲正。未来如要求立即追回，应单独设计积分欠账机制。
6. 撤销流水的 `idempotency_key` 固定使用 `reverse:{originalTransactionId}`。
7. 如果该撤销幂等键已存在，直接返回已有撤销结果，不再创建新的反向流水。
8. 系统创建一条金额相反的 `REVERSE` 流水。
9. 原流水保留，并在新流水中记录 `reversedTransactionId`。
10. 账户余额按反向金额更新。
11. 如果原流水的 `source_type = MANUAL_ADJUSTMENT`，系统必须通过原流水的 `source_id` 找到对应 `student_point_adjustment_requests`，确认其状态为 `APPLIED`，将其更新为 `REVERSED`，并写入 `reverse_transaction_id` 和 `reversed_at`。
12. 创建冲正流水、更新账户余额以及更新人工调整请求必须在同一事务中提交；找不到对应调整请求或调整请求状态不是 `APPLIED` 时，整个冲正事务回滚。
13. 原 `MANUAL_ADJUSTMENT` 积分事件保持 `SUCCEEDED`，因为原事件当时已经成功处理；冲正事实由反向流水和调整请求的 `REVERSED` 状态表达，不把原事件改为 `CANCELLED`。

## 12. 积分规则建议

首期默认规则建议如下：

| 规则编码 | 名称 | 分值 | 幂等或触发说明 |
| --- | --- | ---: | --- |
| `DAILY_TASK_COMPLETED` | 完成今日任务 | +10 | 由 `study-day-task:{studyDayTaskId}:completed` 幂等键防重复 |
| `STUDY_RECORD_CORRECT` | 单词答对 | +1 | 首期不设置每日上限 |
| `WRONG_WORD_REVIEW_CORRECT` | 错词复习答对 | +2 | 首期不设置每日上限 |
| `VIDEO_WATCH_COMPLETED` | 视频观看完成 | +10 | 由 `video-watch:{studentStudyPlanId}:{videoId}:completed` 幂等键防重复 |
| `EXAM_SUBMITTED` | 提交考试 | +5 | 由 `exam:{examId}:submitted` 幂等键防重复 |
| `EXAM_SCORE_PASS` | 考试及格 | +10 | 由考试提交来源和规则编码组成的幂等键防重复 |
| `EXAM_SCORE_EXCELLENT` | 考试优秀 | +20 | 与及格奖励二选一 |
| `STREAK_3_DAYS` | 连续学习 3 天 | +10 | 后续阶段再设计里程碑幂等键 |
| `STREAK_7_DAYS` | 连续学习 7 天 | +30 | 后续阶段再设计里程碑幂等键 |

首期实现接入 `DAILY_TASK_COMPLETED`、`STUDY_RECORD_CORRECT` 和教师/管理员人工调整。视频、考试、错词复习和连续学习积分放到后续阶段；其中视频业务即使暂不发积分，也必须继续遵守“视频完成状态不因外围能力失败而回滚”的原则。

## 13. 权限设计

### 13.1 学生权限

- 只能查看自己的积分账户和流水。
- 不能修改积分。
- 不能查看其他学生积分。
- 不能直接调用自动加分接口。

### 13.2 教师权限

- 只能查看自己管理学生的积分。
- 只能对自己管理学生做人工调整。
- 不能修改积分规则。
- 不能撤销系统自动流水，除非后续加入审批权限。

### 13.3 管理员权限

- 可以查看全局积分账户、流水、规则。
- 可以配置积分规则。
- 可以进行管理员修正、积分事件手动重试、未成功事件取消和流水撤销。
- 所有管理员操作必须记录 `operatorId` 和 `reason`。

## 14. 幂等与一致性

积分服务需要保证以下一致性：

- 同一个幂等键只能产生一条有效流水。
- 每条积分事件最多生成一条积分流水。
- 同一个业务行为如果命中多条规则，应创建多条积分事件。
- `source_key` 表示原始业务来源，可以被多条积分事件共享；`idempotency_key` 表示某条规则发放的唯一键，必须唯一。
- `rule_code` 必须全局唯一且创建后禁止修改，否则会破坏 `source_key + rule_code` 组成的幂等语义。
- 账户余额和流水在同一个事务中提交。
- 写入流水时，`account_id` 和 `student_id` 必须来自已锁定的 `student_point_accounts`，不能直接使用前端请求或外部调用传入的账户 ID。
- `StudentPointService` 必须保证流水中的 `account_id` 与 `student_id` 对应同一个积分账户。
- 积分事件成功处理后，事件状态和生成的流水关联需要在同一事务中提交。
- `MANUAL_ADJUSTMENT` 事件成功处理时，对应 `student_point_adjustment_requests` 的 `APPLIED` 状态和 `transaction_id` 必须与流水、账户余额、事件成功状态在同一事务中提交。
- `MANUAL_ADJUSTMENT` 事件处理失败时，对应 `student_point_adjustment_requests` 的 `FAILED` 状态必须与事件失败状态和尝试记录在同一事务中提交。
- 冲正 `MANUAL_ADJUSTMENT` 流水时，冲正流水、账户余额和对应调整请求的 `REVERSED` 状态、`reverse_transaction_id`、`reversed_at` 必须在同一事务中提交；无法更新对应调整请求时整体回滚。
- 规则修改或停用前必须检查未完成事件；存在 `PENDING`、`PROCESSING`、`FAILED` 事件时拒绝修改或停用。
- 规则修改后，只影响后续新创建的积分事件。
- 扣减积分时必须检查可用余额，不允许扣成负数。
- 教师人工扣减不能超过学生当前可用积分，除非管理员修正允许负向冲正，但账户余额仍不得小于 0。
- 对高并发入账，建议查询账户时使用数据库行锁，或在 repository 层提供 `findByStudentIdForUpdate`。

推荐事务边界：

- `StudentPointService` 的入账、扣减、冻结、撤销方法都使用 `@Transactional`。
- 学习、考试、视频观看等主业务完成状态不因积分事件创建失败或积分入账失败而回滚。
- 主业务完成状态保存成功后，如果积分事件创建失败，应记录错误日志和业务来源信息，返回主业务成功但不展示积分到账提示；后续由管理员或补偿脚本根据 `source_type`、`source_id` 或 `source_key` 补建积分事件。
- 只有在明确设计为短流程强事务的场景，才允许主业务保存和积分事件创建一起失败；长视频、考试提交、学习任务完成默认不采用该模式。
- 主业务重复提交或接口超时重试时，如果积分事件 `idempotency_key` 已存在，应返回已有事件语义，不重复创建事件，也不回滚已经完成的主业务状态。
- 积分事件处理失败时，只更新事件状态、重试次数和失败原因，不回滚已经完成的学习、考试或视频观看状态。
- 自动重试和管理员手动重试都必须先通过数据库条件更新，将事件从 `PENDING` 或 `FAILED` 原子置为 `PROCESSING`，并写入 `processing_started_at`，再执行入账逻辑；只有更新影响行数为 1 时才允许处理。
- `PROCESSING` 状态的事件不得被自动重试、手动重试或取消。
- 服务启动恢复或处理超时时，可以将残留 `PROCESSING` 事件恢复为 `FAILED`，再进入自动重试或管理员手动重试流程。
- 自动重试最多 3 次；管理员手动重试允许突破 3 次自动重试限制，但必须写入 `student_point_event_attempts`。
- MVP 明确只有一个后台积分事件处理服务实例，因此自动重试流程不引入多实例事件领取协议。若未来需要横向扩展多个后台处理实例，必须先补充分布式领取或数据库原子领取设计。
- 积分模块内部保持强一致：流水、账户余额、事件成功状态必须一起提交；不能出现流水成功但余额未更新，或事件成功但没有流水的状态。
- 人工调整积分模块内部也必须保持强一致：不能出现积分已到账但调整请求仍为 `PENDING` 或 `FAILED` 的状态。
- 后续如接入消息队列，可由 `student_point_events` 演进为 outbox 事件表。

## 15. 异常处理

建议异常规则：

- 学生账户不存在：返回系统数据异常并记录错误；新学生必须在创建学生时同步创建积分账户，历史数据缺口通过迁移补齐。
- 幂等键已存在：直接返回已有流水或当前账户，不抛业务错误。
- 创建积分事件时幂等键已存在：按已有事件状态返回；`PENDING`、`PROCESSING`、`FAILED` 表示积分仍在处理或待补发，`SUCCEEDED` 返回已有入账结果，`CANCELLED` 不重新发放。
- 余额不足：返回 400，错误码 `INSUFFICIENT_POINTS`。
- 冲正正向加分流水时可用余额不足：返回 409，错误码 `INSUFFICIENT_POINTS_FOR_REVERSAL`，提示当前余额和需要扣回的积分；不允许部分冲正，也不创建冲正流水或修改人工调整请求。
- 教师无权调整学生：返回 403。
- 积分规则未启用：自动跳过该类积分发放，不影响主业务。
- 规则修改或停用时仍有未完成事件：返回 409，提示管理员先等待事件处理完成、手动重试或取消事件。
- 规则被停用：在没有未完成事件时允许停用，停用后只影响新事件创建。
- 积分事件处理失败：记录 `last_error`、增加 `auto_attempt_count`，设置 `next_retry_at`，并写入 `student_point_event_attempts`。
- 积分事件自动重试达到 3 次：保留 `FAILED` 状态，不再自动重试，交由管理员排查或手动重试。
- 管理员手动重试失败事件：必须填写原因，并写入 `student_point_event_attempts`。
- 管理员手动重试正在处理中的事件：返回 409，错误码可使用 `POINT_EVENT_PROCESSING`。
- 未成功入账的事件取消：状态变为 `CANCELLED`，不再自动或手动发放。
- 已成功事件取消：不允许；如需纠错，必须对生成的积分流水做 `REVERSE` 冲正。
- 人工调整原因为空：返回 400。
- 人工调整积分事件失败：调整请求状态更新为 `FAILED`，不返回 `APPLIED`。
- 人工调整事件自动重试未完成：教师不能重新提交替代性人工调整，返回 409，提示“积分调整正在自动重试中，请稍后查看结果”。
- 人工调整事件自动重试达到 3 次仍失败：教师重新提交替代性人工调整前，必须先取消旧事件，旧事件取消后不得再被管理员手动重试。
- 撤销已撤销流水：如果 `reverse:{originalTransactionId}` 幂等键已存在，直接返回已有撤销结果；否则返回 409。

## 16. 前端展示建议

### 16.1 学生端

学生工作台可增加轻量积分入口：

- 首页顶部显示“当前积分”。
- 今日任务完成后展示积分增加提示。
- 个人中心增加“积分明细”。
- 积分明细按时间倒序展示来源、分值、余额。

首期避免做过强的游戏化视觉，以免干扰学习主流程。

### 16.2 教师端

教师端建议放在班级详情或学生管理中：

- 班级学生积分列表。
- 本周积分榜。
- 学生积分明细抽屉。
- 人工加减分弹窗。

### 16.3 管理员端

管理员端建议作为运营管理工具：

- 积分规则配置。
- 全局流水查询。
- 异常调整记录。
- 撤销和修正入口。

## 17. 测试策略

### 17.1 单元测试

重点测试 `StudentPointService`：

- 创建学生时同步创建积分账户。
- 积分入账时学生账户不存在应失败并记录数据异常，不在入账流程隐式创建账户。
- 正向积分增加余额和累计获得。
- 负向扣减减少余额和累计消耗。
- 幂等键重复不会重复加分。
- 余额不足扣减失败。
- 撤销流水生成反向流水。
- 重复提交同一流水撤销时，`reverse:{originalTransactionId}` 幂等键不会生成第二条反向流水。
- 冲正正向加分流水时余额不足，整笔冲正失败且不产生冲正流水；余额足够后可以重新发起并成功完成整笔冲正。
- 冲正不得产生负余额，也不允许只冲正原流水的一部分。
- 冲正人工调整流水时，对应调整请求从 `APPLIED` 更新为 `REVERSED`，并记录冲正流水和冲正时间；原积分事件仍保持 `SUCCEEDED`。
- 冲正人工调整流水时，如果无法找到对应调整请求或其状态不是 `APPLIED`，冲正流水和账户余额更新必须一起回滚。
- 首期不校验规则次数限制或额度限制。

重点测试 `StudentPointEventService`：

- 主业务完成后能按命中的积分规则创建一条或多条积分事件。
- 每条积分事件最多生成一条积分流水。
- 事件创建时固化 `source_key`、`rule_code`、`points` 和 `idempotency_key`。
- `MANUAL_ADJUSTMENT` 事件创建时必须设置 `source_id = adjustmentRequestId`。
- 重复创建同一 `idempotency_key` 的积分事件时，不新增事件；按已有事件状态返回结果。
- 规则存在未完成事件时，修改或停用应被拒绝。
- 规则修改后，新创建事件使用新规则。
- 事件处理成功后生成流水并关联 `transaction_id`。
- 事件处理失败时记录失败原因、自动重试次数和下次重试时间。
- 自动重试达到 3 次后不再自动处理。
- 管理员手动重试会写入 `student_point_event_attempts`，且不受 3 次自动重试限制。
- 3 次自动失败后，管理员手动重试的 `attempt_no` 应从 4 开始继续递增，且不增加 `auto_attempt_count`。
- 自动重试和管理员手动重试都必须先把事件置为 `PROCESSING`。
- `PROCESSING` 状态下再次手动重试返回 409。
- 服务重启后，残留 `PROCESSING` 事件会恢复为 `FAILED` 并允许后续重试。
- `PROCESSING` 超过 10 分钟仍未完成时，会恢复为 `FAILED`，避免事件永久卡死。
- 未成功事件可以取消，成功事件不能取消，只能通过流水冲正。
- 重复处理同一事件不会重复生成流水。

### 17.2 服务集成测试

重点覆盖：

- 学习记录提交后触发积分。
- 每日任务首次完成才发积分。
- 后续接入视频积分时，视频观看完成后创建积分事件，积分失败不回滚视频学习完成状态。
- 失败积分事件可以通过后台重试补发，并最终生成流水。
- 考试优秀等单个业务行为命中多条规则时，会创建多条积分事件，且失败重试互不影响。
- 考试提交后按分数发积分。
- 教师只能调整自己管理的学生。
- 教师人工调整创建后先进入 `PENDING`，入账成功后变为 `APPLIED`，入账失败后变为 `FAILED`。
- 教师人工调整事件通过 `source_id` 回写原调整请求状态和 `transaction_id`。
- 教师人工调整入账成功时，流水、账户余额、事件 `SUCCEEDED`、调整请求 `APPLIED` 和尝试记录必须同事务提交。
- 教师人工调整入账失败时，事件 `FAILED`、调整请求 `FAILED` 和尝试记录必须同事务提交。
- 教师人工调整失败后，自动重试完成前不能重新提交替代性人工调整。
- 教师人工调整自动重试成功后，原调整请求会从 `FAILED` 更新为 `APPLIED` 并记录流水。
- 教师人工调整自动重试 3 次后仍失败时，重新提交替代调整必须先取消旧事件，旧事件取消后不能再手动重试。
- 教师人工调整成功后被管理员冲正时，调整请求、冲正流水和账户余额在同一事务中更新，调整请求最终状态为 `REVERSED`。
- 学生只能查询自己的积分。

### 17.3 API 测试

重点覆盖：

- `GET /api/students/me/points`
- `GET /api/students/me/points/transactions`
- 教师班级积分列表。
- 教师人工调整。
- 管理员规则启停、失败事件查询、手动重试、取消未成功事件。

## 18. MVP 范围

建议第一阶段只做下面能力：

- 学生积分账户。
- 积分流水。
- 积分事件表，用于记录待发放、失败和重试的积分任务。
- 积分事件处理尝试表，用于记录自动处理和管理员手动重试审计。
- 幂等入账。
- 学生查看积分概况和明细。
- 学习计划“完成今日任务”自动加分。
- 学习记录“答对单词”自动加分，首期不设置每日上限。
- 首期不接入视频积分；后续接入时，积分失败不得影响视频学习完成状态。
- 教师对自己管理学生人工加减分。
- 管理员查看流水和进行修正，规则先使用数据库种子或代码默认值。

暂不进入 MVP：

- 积分商城。
- 积分冻结和兑换完成。
- 全局排行榜。
- 徽章系统。
- 复杂连续学习里程碑。
- 异步事件总线。

## 19. 分阶段落地建议

### 第一阶段：积分基础闭环

- 新增账户表、流水表、事件表、事件处理尝试表、规则表、人工调整表。
- 实现 `StudentPointService`。
- 实现 `StudentPointEventService` 和失败重试机制。
- 接入学习计划完成和单词答对事件。
- 接入每日任务完成和学习记录答对事件。
- 提供学生积分概况和明细接口。
- 提供教师人工调整接口。

### 第二阶段：教学激励增强

- 接入考试积分。
- 接入错词复习积分。
- 增加连续学习奖励。
- 增加班级积分榜和学生周榜。
- 增加管理员规则配置接口。

### 第三阶段：运营与兑换

- 增加积分兑换表。
- 支持冻结、确认消耗、取消解冻。
- 增加徽章或等级。
- 增加 outbox 异步积分事件，降低业务模块耦合。

## 20. 验收标准

第一阶段完成后应满足：

- 学生完成今日学习任务后，积分账户增加固定积分。
- 首期不发放视频积分；视频学习完成状态仍不得因外围能力失败而回滚。
- 积分计算失败时，系统能保留失败积分事件，并在后续重试成功后补发积分。
- MVP 只有一个后台积分事件处理服务实例；自动重试不需要多实例领取协议。
- 自动重试最多 3 次；超过 3 次后不再自动重试，管理员可以手动重试并留下审计记录。
- 积分事件处于 `PROCESSING` 时，不能被重复处理、手动重试或取消。
- 同一业务行为命中多条积分规则时，应创建多条积分事件，每条事件最多生成一条流水。
- 规则存在未完成积分事件时，不允许修改或停用；规则修改后只影响后续新创建事件。
- 未成功入账的积分事件可以取消；已成功入账的事件只能通过流水冲正纠错。
- 正向加分流水冲正时余额不足应返回 `INSUFFICIENT_POINTS_FOR_REVERSAL`，不产生部分冲正或负余额；余额充足后可重新发起整笔冲正。
- 同一任务重复提交或重复刷新不会重复加分。
- 学生答对单词可获得积分；首期不设置答题次数限制或每日额度限制。
- 学生能查看自己的积分余额和流水。
- 教师能查看自己管理学生的积分，并能填写原因进行加减分。
- 教师人工调整请求状态能准确表达 `PENDING`、`APPLIED`、`FAILED`、`REVERSED`。
- 教师不能查看或调整非自己管理的学生。
- 管理员可以查看流水并进行修正。
- 所有积分变化都有流水记录，且流水能说明来源、操作者和原因。
- 积分余额不能小于 0。

## 21. 结论

学生积分管理模块应被设计为独立的激励与审计层，而不是学习计划或考试模块的附属字段。推荐采用“积分账户 + 积分事件 + 积分事件处理尝试 + 积分流水 + 积分规则 + 幂等键”的方案：学习、考试、视频观看、教师调整等模块只提供可信业务事件，积分服务统一负责事件创建、快照固化、重试补发、入账、扣减和审计。

该设计与当前项目的 Spring Boot 分层、角色权限、学习计划和学生记忆模块都能自然衔接，首期可以用较小范围形成闭环，后续再扩展排行榜、兑换、徽章和异步事件化。

## 22. 首期实现定稿补充

本节记录开发阶段已经落定的实现约束；如与前文的早期建议冲突，以本节为准。

### 22.1 首期范围

- 自动积分只接入“每日任务首次完成”和“学习记录答对单词”。
- 支持教师对受管学生人工加减分，支持管理员人工修正、事件手动重试、取消未成功事件和流水冲正。
- 首期不接入视频积分、考试积分、积分商城、排行榜、次数限制和每日额度限制。

### 22.2 学习业务与积分的可靠交接

- 学习记录提交请求必须携带 `request_key`。同一请求键重复提交相同载荷时返回原结果，载荷不同时返回幂等冲突，避免接口超时重试产生重复学习记录和重复积分。
- 新创建的 `study_records` 才设置 `points_eligible = true`；迁移前的历史记录保持 `false`，上线后不自动追溯发分。
- `study_day_tasks` 只有在任务数大于 0 且首次从未完成变为完成时才设置 `points_eligible = true`。空任务不能获得每日任务完成积分。
- 主业务事务提交后，通过有界单线程执行器异步创建积分事件，不阻塞主业务响应。
- 定时补偿任务扫描 `points_eligible = true` 且缺少对应积分事件的学习记录和每日任务，按相同 `source_key`、`rule_code` 和幂等键补建事件。
- 前端在 `sessionStorage` 中保存未完成提交的请求键和载荷指纹。页面刷新或网络重试复用原请求键，成功后清理；载荷改变时生成新请求键。

### 22.3 事件处理事务边界

积分事件一次处理明确拆成三个事务边界：

1. 领取事务：通过数据库条件更新把 `PENDING` 或可重试的 `FAILED` 原子改为 `PROCESSING`，提交后才进入入账逻辑。
2. 入账事务：锁定积分账户，在同一事务中更新账户、写积分流水、写成功尝试记录，并把事件改为 `SUCCEEDED`、关联 `transaction_id`。
3. 失败记录事务：入账事务失败并回滚后，以 `REQUIRES_NEW` 单独记录失败尝试、错误信息、自动重试次数和下次重试时间；人工调整事件同时更新对应调整请求状态。

服务启动时只恢复本次启动前遗留的 `PROCESSING` 事件；运行中超过处理超时时间的事件由超时恢复任务改为 `FAILED`。首期仍严格保持一个后台积分处理服务实例。

### 22.4 人工调整幂等与替代关系

- `student_point_adjustment_requests.request_key` 全局唯一。同一请求键只能对应同一学生、分值、原因、操作人和替代目标。
- 初次人工调整的“调整请求 + 积分事件”在同一事务创建。
- 替代调整必须在旧事件自动重试达到 3 次且仍失败后进行；旧事件取消、旧请求记录替代关系、新请求和新事件创建在同一事务完成。
- 自动重试结束前不允许教师创建替代调整；被替代并取消的旧事件不能再由管理员手动重试。

### 22.5 数据库迁移

- `V31` 创建积分账户、流水、事件、处理尝试、规则和人工调整请求表，并为历史学生补齐唯一积分账户。
- `V32` 为人工调整增加请求级幂等键和结构化替代关系字段。
- `V33` 为学习记录增加请求级幂等键，并为历史数据生成不可冲突的回填值。
- `V34` 为学习记录和每日任务增加 `points_eligible`，历史数据默认不具备补发资格。
- `V35` 增加 `student_point_rule_audits`，记录规则创建和更新的管理员、原因及变更前后快照；该审计表不建立级联删除关系。
