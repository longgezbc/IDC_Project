# 数据库迁移管理（Flyway）

本目录存放所有 Flyway 数据库迁移脚本。Flyway 在应用启动时自动检测并应用未执行的迁移脚本，
并将执行历史记录到数据库的 `flyway_schema_history` 表中。

## 命名规范

```
V{YYYYMMDD}__{snake_case_description}.sql
        ↑                ↑
   版本号（数字）    双下划线分隔版本与描述
```

**示例：**
- `V20260720__add_composite_indexes.sql` ✓
- `V20260704__project_authorization_transfer.sql` ✓
- `V20260720_1__add_composite_indexes.sql` ✗（避免使用 `_数字` 子版本，保持版本号简洁）

**版本号规则：**
- 版本号必须单调递增（按时间排列即可）
- 已发布的版本号不可复用（不可修改已应用的脚本）
- 新脚本版本号必须大于当前最大版本号

## 编写规范

### 1. 必须幂等

通过 `information_schema` 检查对象是否存在后再执行 DDL，避免重复执行报错：

```sql
-- 检查列是否存在
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project'
      AND COLUMN_NAME = 'version');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_project` ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本号''',
    'SELECT ''biz_project.version already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

### 2. 禁止使用 USE 语句

Flyway 通过 `datasource.url` 已经指定了目标数据库，使用 `USE database;` 会导致事务边界异常。
脚本中所有表名应直接使用 `biz_project` 等名称（不带数据库前缀）。

### 3. 视图与存储过程

使用 `DROP IF EXISTS + CREATE` 模式重建：

```sql
DROP VIEW IF EXISTS `v_pending_progress_review`;
CREATE VIEW `v_pending_progress_review` AS
SELECT ... ;
```

### 4. 字典/配置数据

使用 `DELETE WHERE` 清理后再 `INSERT`，或使用 `INSERT ... ON DUPLICATE KEY UPDATE`：

```sql
INSERT INTO `sys_permission` (`id`, `code`, ...) VALUES (81, 'document:review', ...)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
```

### 5. 字符编码

- 所有脚本必须使用 UTF-8 编码（`application.yml` 中 `spring.flyway.encoding: UTF-8`）
- 字符串字面量中的单引号需双写转义：`'It''s a test'`

## 部署流程

### 全新部署（首次安装）

1. Docker 初始化 MySQL 容器时执行 `docker/mysql/01-schema.sql` 与 `02-seed-data.sql`（通过 `docker-entrypoint-initdb.d`）
2. 后端应用首次启动时，Flyway 检测到数据库为空（无 `flyway_schema_history` 表），
   - `baseline-on-migrate=true` 自动创建 baseline（版本 0）
   - 按版本号顺序应用 `db/migration/` 下所有脚本
3. 应用启动完成，数据库就绪

### 已有数据库（首次接入 Flyway）

适用于：服务器数据库已存在历史数据，迁移脚本曾手动执行过。

1. 部署新版本后端应用（包含 Flyway 依赖）
2. 应用首次启动时：
   - Flyway 检测到 `flyway_schema_history` 表不存在
   - 创建 baseline 记录（版本 0，标记"当前数据库状态"）
   - 按版本号顺序应用所有未执行的迁移脚本
3. 由于所有迁移脚本均做了幂等处理（`information_schema` 存在性检查），
   重复执行的脚本会跳过实际 DDL，仅记录到 `flyway_schema_history`
4. 后续启动只应用新增的迁移脚本

**关键配置（已在 `application.yml` 中固化）：**
- `baseline-on-migrate: true` —— 允许在非空数据库上启用 Flyway
- `baseline-version: 0` —— 将当前数据库状态标记为版本 0
- `validate-on-migrate: false` —— 跳过校验已有脚本的 checksum（避免历史脚本手动修改后启动失败）

### 日常开发流程（新增迁移）

1. 在本目录新增 `V{YYYYMMDD}__{description}.sql` 文件
2. 编写幂等 DDL/DDL 脚本
3. 本地启动应用，Flyway 自动应用迁移并验证
4. 提交到版本控制
5. 部署到服务器后，应用启动时自动应用

## 常用维护命令

### 查看已应用的迁移

```sql
SELECT installed_rank, version, description, type, script, installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### 手动修复 Flyway 历史

当某次迁移执行失败留下"半完成"状态时：

```sql
-- 删除失败的记录（仅当 success=0 时）
DELETE FROM flyway_schema_history WHERE success = 0;

-- 然后修复数据库状态后重启应用
```

### 跳过某次迁移（紧急回滚场景）

不推荐，但若必须：

```sql
-- 手动插入"已执行"记录
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES (?,?,?,?,?,?,CURRENT_USER,0,1);
```

## 当前迁移清单

| 版本 | 描述 | 类型 | 状态 |
|------|------|------|------|
| 20260704 | backfill_manager_assignee_ids | 数据修复 | 历史已手动执行 |
| 20260704 | project_authorization_transfer | 架构变更 | 历史已手动执行 |
| 20260705 | organization_architecture_upgrade | 架构变更 | 历史已手动执行 |
| 20260712 | add_optimistic_lock_version | DDL | 历史已手动执行 |
| 20260714 | add_actual_dates_and_document_review | DDL+DML | 历史已手动执行 |
| 20260715 | remove_workload_fields | DDL+DDL | 历史已手动执行 |
| 20260720 | add_composite_indexes | DDL | **首次 Flyway 执行** |

## 故障排查

### 启动报错 "Validate failed: Migration checksum mismatch"

**原因：** 已应用的迁移脚本被修改了。
**解决：**
1. 临时设置 `spring.flyway.validate-on-migrate=false` 启动一次
2. 或在数据库中更新对应记录的 checksum：
   ```sql
   UPDATE flyway_schema_history SET checksum = NULL WHERE version = 'XXXXXX';
   ```
3. 重启应用

### 启动报错 "Detected failed migration to version X"

**原因：** 上次迁移执行失败留下"半完成"状态。
**解决：**
1. 手动修复数据库到一致状态
2. 删除 `flyway_schema_history` 中 `success=0` 的记录：
   ```sql
   DELETE FROM flyway_schema_history WHERE success = 0;
   ```
3. 重启应用

### 启动报错 "Unable to insert row for version X in metadata table"

**原因：** 权限不足或表锁冲突。
**解决：**
1. 确认数据库用户有 `CREATE TABLE`、`INSERT`、`UPDATE` 权限
2. 检查是否有未提交的事务锁住 `flyway_schema_history` 表
