
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

-- ============================================================================
-- 创建应用数据库用户（非 root），仅授予 hbsz_idc_system 库权限
-- 安全要求：后端服务不应使用 root 账号连接数据库
-- 密码同步：此处的 hbsz_app_2026 仅作首次初始化占位，实际密码由 mysql-grants
--          容器在 MySQL 启动后通过 ALTER USER 同步为 .env.docker 中的 DB_PASSWORD
-- ============================================================================
CREATE USER IF NOT EXISTS 'hbsz_app'@'%' IDENTIFIED BY 'hbsz_app_2026';
-- 应用用户需要完整的 DDL/DML 权限以支持 Flyway 迁移（CREATE/ALTER/DROP flyway_schema_history 等）
GRANT ALL PRIVILEGES ON `hbsz_idc_system`.* TO 'hbsz_app'@'%';
-- Flyway 需要读取 performance_schema 中的系统变量（如 foreign_key_checks、user_variables_by_thread）
GRANT SELECT ON performance_schema.* TO 'hbsz_app'@'%';
-- MySQL 8.0+ 迁移脚本中使用 SET @变量 + PREPARE 动态 SQL 需要 SYSTEM_USER 权限
GRANT SYSTEM_USER ON *.* TO 'hbsz_app'@'%';
FLUSH PRIVILEGES;

DROP TABLE IF EXISTS `biz_archive_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_archive_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `project_code` varchar(50) DEFAULT NULL COMMENT '项目编号（冗余）',
  `project_name` varchar(200) DEFAULT NULL COMMENT '项目名称（冗余）',
  `operation_type` varchar(20) NOT NULL COMMENT '操作类型：archive-归档，restore-恢复',
  `archive_date` date DEFAULT NULL COMMENT '归档日期',
  `reason` varchar(500) DEFAULT NULL COMMENT '归档/恢复原因',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_archive_date` (`archive_date`),
  KEY `fk_ar_operator` (`operator_id`),
  CONSTRAINT `fk_ar_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ar_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目归档记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `customer_code` varchar(50) NOT NULL COMMENT '客户编号',
  `name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(50) NOT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `contact_email` varchar(100) DEFAULT NULL COMMENT '联系邮箱',
  `address` varchar(300) DEFAULT NULL COMMENT '客户地址',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_code` (`customer_code`),
  KEY `idx_name` (`name`),
  KEY `idx_contact_phone` (`contact_phone`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_design_drawing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_design_drawing` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '图纸ID',
  `project_id` bigint NOT NULL COMMENT '所属项目ID',
  `name` varchar(200) NOT NULL COMMENT '图纸名称',
  `drawing_type` varchar(50) DEFAULT '图纸' COMMENT '图纸类型',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件存储路径',
  `file_size` varchar(20) DEFAULT NULL COMMENT '文件大小（如2.4MB）',
  `file_size_bytes` bigint DEFAULT '0' COMMENT '文件大小（字节）',
  `upload_date` date DEFAULT NULL COMMENT '上传日期',
  `uploaded_by` bigint DEFAULT NULL COMMENT '上传人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_upload_date` (`upload_date`),
  KEY `fk_drawing_uploader` (`uploaded_by`),
  CONSTRAINT `fk_drawing_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_drawing_uploader` FOREIGN KEY (`uploaded_by`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设计图纸表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `document_code` varchar(50) NOT NULL COMMENT '文档编号（前端DocumentManagement.vue中row.code字段）',
  `project_id` bigint NOT NULL COMMENT '所属项目ID',
  `name` varchar(200) NOT NULL COMMENT '文档名称',
  `category` varchar(30) NOT NULL COMMENT '文档分类：合同文件/技术文档/验收报告/设计图纸/施工记录/其他',
  `current_version` varchar(20) DEFAULT 'V1.0' COMMENT '当前版本号',
  `status` varchar(20) NOT NULL DEFAULT '待审核' COMMENT '审核状态：待审核/已审核/已驳回',
  `file_format` varchar(20) DEFAULT NULL COMMENT '文件格式：PDF/DOCX/XLSX/DWG等（前端format/fileType字段）',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件存储路径',
  `file_size` varchar(20) DEFAULT NULL COMMENT '文件大小（显示用，如3.2MB）',
  `file_size_bytes` bigint DEFAULT '0' COMMENT '文件大小（字节）',
  `description` varchar(500) DEFAULT NULL COMMENT '文档描述',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传人ID',
  `uploader_name` varchar(50) DEFAULT NULL COMMENT '上传人姓名（冗余，前端uploader字段）',
  `upload_date` date NOT NULL COMMENT '上传日期',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_code` (`document_code`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_uploader_id` (`uploader_id`),
  KEY `idx_name` (`name`),
  KEY `fk_doc_reviewer` (`reviewer_id`),
  KEY `idx_upload_date_id` (`upload_date`,`id`),
  KEY `idx_project_status_category` (`project_id`,`status`,`category`),
  CONSTRAINT `fk_doc_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_doc_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_doc_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `tag_name` varchar(50) NOT NULL COMMENT '标签名称',
  `tag_color` varchar(20) DEFAULT NULL COMMENT '标签颜色',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档标签表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document_tag_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document_tag_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_tag` (`document_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_dtr_document` FOREIGN KEY (`document_id`) REFERENCES `biz_document` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dtr_tag` FOREIGN KEY (`tag_id`) REFERENCES `biz_document_tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_document_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `version` varchar(20) NOT NULL COMMENT '版本号（如V1.0、V2.1）',
  `file_url` varchar(500) DEFAULT NULL COMMENT '该版本文件路径',
  `file_size` varchar(20) DEFAULT NULL COMMENT '文件大小',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传人ID',
  `uploader_name` varchar(50) DEFAULT NULL COMMENT '上传人姓名',
  `remark` varchar(500) DEFAULT NULL COMMENT '版本备注',
  `upload_date` date NOT NULL COMMENT '上传日期',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_version` (`version`),
  KEY `fk_version_uploader` (`uploader_id`),
  KEY `idx_doc_upload` (`document_id`,`upload_date`),
  CONSTRAINT `fk_version_document` FOREIGN KEY (`document_id`) REFERENCES `biz_document` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_version_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档版本表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_export_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_export_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '导出人ID',
  `user_name` varchar(64) DEFAULT NULL COMMENT '导出人姓名',
  `report_type` varchar(32) NOT NULL DEFAULT 'daily' COMMENT '报告类型',
  `start_date` date NOT NULL COMMENT '统计开始日期',
  `end_date` date NOT NULL COMMENT '统计结束日期',
  `format` varchar(10) NOT NULL DEFAULT 'xlsx' COMMENT '导出格式',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_path` varchar(512) NOT NULL COMMENT '文件存储路径',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小',
  `status` varchar(20) NOT NULL DEFAULT 'success' COMMENT '导出状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_er_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `project_code` varchar(50) NOT NULL COMMENT '项目编号（如 HB-WH-2024-001）',
  `name` varchar(200) NOT NULL COMMENT '项目名称',
  `status` varchar(20) NOT NULL DEFAULT '进行中' COMMENT '项目状态：进行中/已完成/已归档/暂停/延期',
  `priority` varchar(10) NOT NULL DEFAULT '中' COMMENT '优先级：高/中/低',
  `project_type` varchar(20) NOT NULL DEFAULT '新建' COMMENT '项目类型：新建/改造/维护',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID',
  `manager_id` bigint DEFAULT NULL COMMENT '项目经理用户ID',
  `manager_name` varchar(50) DEFAULT NULL COMMENT '项目经理姓名（冗余字段，便于查询）',
  `manager_phone` varchar(20) DEFAULT NULL COMMENT '项目经理电话',
  `address` varchar(300) DEFAULT NULL COMMENT '项目地址',
  `region` varchar(100) DEFAULT NULL COMMENT '项目地区（省/市/区，/ 分隔）',
  `start_date` date NOT NULL COMMENT '计划开始日期',
  `end_date` date NOT NULL COMMENT '计划结束日期',
  `actual_start_date` date DEFAULT NULL COMMENT '实际开工日期',
  `actual_end_date` date DEFAULT NULL COMMENT '实际完工日期',
  `contract_amount` decimal(14,2) DEFAULT '0.00' COMMENT '合同金额（元）',
  `budget` decimal(14,2) DEFAULT '0.00' COMMENT '预算金额（元）',
  `progress` int DEFAULT '0' COMMENT '项目进度（0-100）',
  `description` text COMMENT '项目描述',
  `is_archived` tinyint DEFAULT '0' COMMENT '是否已归档：0-未归档，1-已归档',
  `archive_date` date DEFAULT NULL COMMENT '归档日期',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `team_scope` varchar(20) NOT NULL DEFAULT 'assignment' COMMENT '项目可见范围：assignment=仅授权可见/team=项目组内可见/public=全公司可见',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观锁版本号（并发更新保护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_project_type` (`project_type`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_manager_id` (`manager_id`),
  KEY `idx_is_archived` (`is_archived`),
  KEY `idx_start_date` (`start_date`),
  KEY `idx_end_date` (`end_date`),
  KEY `fk_project_creator` (`created_by`),
  KEY `idx_created_at_id` (`created_at`,`id`),
  KEY `idx_manager_status_archived` (`manager_id`,`status`,`is_archived`),
  CONSTRAINT `fk_project_creator` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_project_customer` FOREIGN KEY (`customer_id`) REFERENCES `biz_customer` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_project_manager` FOREIGN KEY (`manager_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_project_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_project_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `engineer_id` bigint NOT NULL COMMENT '工程师用户ID',
  `assigned_by` bigint NOT NULL COMMENT '授权人（PM）用户ID',
  `assigned_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否有效：1有效 / 0已撤销',
  `remark` varchar(500) DEFAULT NULL COMMENT '授权备注',
  `permission_scope` varchar(20) NOT NULL DEFAULT 'read_write' COMMENT '授权范围：read_only只读/read_write读写/review可审核',
  `expire_at` datetime DEFAULT NULL COMMENT '授权过期时间（NULL=永久）',
  `revoke_reason` varchar(200) DEFAULT NULL COMMENT '撤销原因',
  `revoked_at` datetime DEFAULT NULL COMMENT '实际撤销时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_active` (`project_id`,`is_active`),
  KEY `idx_assigned_by` (`assigned_by`),
  KEY `idx_engineer_active_expire` (`engineer_id`,`is_active`,`expire_at`),
  CONSTRAINT `fk_pa_assigner` FOREIGN KEY (`assigned_by`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pa_engineer` FOREIGN KEY (`engineer_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pa_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目-工程师授权关系表';
-- 函数唯一索引（V20260821 修复）：仅约束活跃记录唯一（is_active=1 取组合键，撤销历史为
-- NULL 不受约束）。原 4 列唯一索引含 is_active，会导致"撤销后再授权"的第二次撤销
-- 与历史撤销记录撞索引（Duplicate entry '24-16-0-read_write'），已废弃。
-- 函数索引不能内嵌 CREATE TABLE，需建表后单独创建（表达式双层括号为 MySQL 语法要求）。
CREATE UNIQUE INDEX `uk_project_engineer_active` ON `biz_project_assignment`
  ((CASE WHEN `is_active` = 1 THEN CONCAT(`project_id`, '_', `engineer_id`, '_', `permission_scope`) ELSE NULL END));
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_project_auth_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_project_auth_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `applicant_id` bigint NOT NULL COMMENT '申请人用户ID',
  `target_engineer_id` bigint NOT NULL COMMENT '目标工程师用户ID',
  `scope` varchar(20) NOT NULL DEFAULT 'read_write' COMMENT '申请范围：read_only/read_write/review',
  `expire_at` datetime DEFAULT NULL COMMENT '申请有效期',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请理由',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/rejected',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approved_at` datetime DEFAULT NULL COMMENT '审批时间',
  `comment` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_applicant` (`applicant_id`),
  KEY `idx_target` (`target_engineer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_project_status` (`project_id`,`status`),
  KEY `fk_par_approver` (`approver_id`),
  CONSTRAINT `fk_par_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_par_approver` FOREIGN KEY (`approver_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_par_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_par_target` FOREIGN KEY (`target_engineer_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目授权申请表';
-- 函数唯一索引（与 V20260819.1 迁移对齐，review W5）：同一 (project, target_engineer)
-- 至多一条 pending 申请；非 pending 为 NULL 不受约束（唯一索引对 NULL 不生效）。
-- 函数索引不能内嵌 CREATE TABLE，需建表后单独创建（表达式双层括号为 MySQL 语法要求）。
CREATE UNIQUE INDEX `uk_auth_request_pending` ON `biz_project_auth_request`
  ((CASE WHEN `status` = 'pending' THEN CONCAT(`project_id`, '_', `target_engineer_id`) ELSE NULL END));
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_project_team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_project_team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `user_id` bigint NOT NULL COMMENT '成员用户ID',
  `role_in_team` varchar(20) NOT NULL DEFAULT 'member' COMMENT '组内角色：leader/member/observer',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `left_at` datetime DEFAULT NULL COMMENT '离组时间',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否在组：1是 0否',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user_active` (`project_id`,`user_id`,`is_active`),
  KEY `idx_user` (`user_id`,`is_active`),
  KEY `idx_project_active_role` (`project_id`,`is_active`,`role_in_team`),
  CONSTRAINT `fk_pt_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pt_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目组成员表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_project_transfer_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_project_transfer_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `project_name` varchar(200) DEFAULT NULL COMMENT '项目名称（冗余）',
  `action_type` varchar(20) NOT NULL COMMENT '操作类型：assign/revoke/transfer',
  `from_engineer_id` bigint DEFAULT NULL COMMENT '原工程师ID',
  `from_engineer_name` varchar(50) DEFAULT NULL COMMENT '原工程师姓名',
  `to_engineer_id` bigint DEFAULT NULL COMMENT '目标工程师ID',
  `to_engineer_name` varchar(50) DEFAULT NULL COMMENT '目标工程师姓名',
  `operated_by` bigint NULL COMMENT '操作人ID（系统自动操作时为NULL，如授权过期定时回收）',
  `operator_name` varchar(50) NOT NULL COMMENT '操作人姓名',
  `operated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '操作备注',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_operated_by` (`operated_by`),
  KEY `idx_operated_at` (`operated_at`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_from_engineer` (`from_engineer_id`),
  KEY `idx_to_engineer` (`to_engineer_id`),
  KEY `idx_project_operated` (`project_id`,`operated_at`),
  CONSTRAINT `fk_ptl_from_engineer` FOREIGN KEY (`from_engineer_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ptl_operator` FOREIGN KEY (`operated_by`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ptl_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ptl_to_engineer` FOREIGN KEY (`to_engineer_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目转移/授权操作审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `project_id` bigint NOT NULL COMMENT '所属项目ID',
  `name` varchar(200) NOT NULL COMMENT '任务名称',
  `parent_id` bigint DEFAULT NULL COMMENT '父任务ID（NULL表示顶级任务）',
  `start_date` date NOT NULL COMMENT '计划开始日期',
  `end_date` date NOT NULL COMMENT '计划结束日期',
  `actual_start_date` date DEFAULT NULL COMMENT '实际开始日期',
  `actual_end_date` date DEFAULT NULL COMMENT '实际结束日期',
  `progress` int DEFAULT '0' COMMENT '任务进度（0-100）',
  `status` varchar(20) NOT NULL DEFAULT '未开始' COMMENT '任务状态：未开始/进行中/已完成/已延期/已暂停',
  `assignee_id` bigint DEFAULT NULL COMMENT '负责人ID',
  `assignee_name` varchar(50) DEFAULT NULL COMMENT '负责人姓名（冗余）',
  `is_milestone` tinyint DEFAULT '0' COMMENT '是否里程碑：0-否，1-是',
  `is_key_task` tinyint DEFAULT '0' COMMENT '是否关键任务：0-否，1-是',
  `sort` int DEFAULT '0' COMMENT '排序号',
  `description` varchar(500) DEFAULT NULL COMMENT '任务描述',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`),
  KEY `idx_assignee_id` (`assignee_id`),
  KEY `idx_is_key_task` (`is_key_task`),
  KEY `idx_is_milestone` (`is_milestone`),
  KEY `idx_start_date` (`start_date`),
  KEY `idx_end_date` (`end_date`),
  KEY `fk_task_creator` (`created_by`),
  KEY `idx_project_sort_start` (`project_id`,`sort`,`start_date`),
  KEY `idx_project_keytask_status_enddate` (`project_id`,`is_key_task`,`status`,`end_date`),
  KEY `idx_assignee_status_end` (`assignee_id`,`status`,`end_date`),
  KEY `idx_project_status_sort` (`project_id`,`status`,`sort`),
  KEY `idx_assignee_project` (`assignee_id`,`project_id`),
  CONSTRAINT `fk_task_assignee` FOREIGN KEY (`assignee_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_task_creator` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_task_parent` FOREIGN KEY (`parent_id`) REFERENCES `biz_task` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_task_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=85 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `biz_task_progress_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_task_progress_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `progress` int NOT NULL COMMENT '填报进度（0-100）',
  `previous_progress` int DEFAULT '0' COMMENT '上次进度',
  `remark` varchar(500) DEFAULT NULL COMMENT '填报备注',
  `images` text COMMENT '施工图片URL列表(JSON数组)',
  `actual_start_date` date DEFAULT NULL COMMENT '实际开工日期',
  `actual_end_date` date DEFAULT NULL COMMENT '实际结束日期',
  `review_status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '审核状态: pending/approved/rejected',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `reviewer_name` varchar(50) DEFAULT NULL COMMENT '审核人姓名',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '审核意见',
  `reporter_id` bigint DEFAULT NULL COMMENT '填报人ID',
  `reporter_name` varchar(50) DEFAULT NULL COMMENT '填报人姓名',
  `report_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '填报时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_report_time` (`report_time`),
  KEY `fk_pl_reporter` (`reporter_id`),
  KEY `idx_task_report` (`task_id`,`report_time`),
  KEY `idx_review_status_time` (`review_status`,`report_time`),
  KEY `idx_project_review_time` (`project_id`,`review_status`,`report_time`),
  KEY `fk_pl_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_pl_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pl_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pl_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pl_task` FOREIGN KEY (`task_id`) REFERENCES `biz_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务进度填报记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `name` varchar(100) NOT NULL COMMENT '部门名称',
  `parent_id` bigint DEFAULT NULL COMMENT '父部门ID，NULL表示顶级部门',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `leader` varchar(50) DEFAULT NULL COMMENT '部门负责人',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_dept_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_department` (`id`) ON DELETE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型编码',
  `dict_label` varchar(100) NOT NULL COMMENT '字典标签（显示值）',
  `dict_value` varchar(100) NOT NULL COMMENT '字典键值（存储值）',
  `css_class` varchar(100) DEFAULT NULL COMMENT '样式属性（前端Tag类型）',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dict_type` (`dict_type`),
  KEY `idx_dict_value` (`dict_value`)
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
  `dict_name` varchar(100) NOT NULL COMMENT '字典名称',
  `dict_type` varchar(100) NOT NULL COMMENT '字典类型编码',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` text COMMENT '通知内容',
  `notify_type` varchar(30) NOT NULL COMMENT '通知类型：progress/task_due/doc_update/archive/system',
  `biz_type` varchar(30) DEFAULT NULL COMMENT '业务类型：project/task/document',
  `biz_id` bigint DEFAULT NULL COMMENT '业务ID',
  `priority` tinyint DEFAULT '1' COMMENT '优先级：1-普通，2-重要，3-紧急',
  `is_read` tinyint DEFAULT '0' COMMENT '是否已读：0-未读，1-已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`is_read`),
  KEY `idx_notify_type` (`notify_type`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_user_read_created` (`user_id`,`is_read`,`created_at`),
  KEY `idx_user_type_created` (`user_id`,`notify_type`,`created_at`),
  CONSTRAINT `fk_notif_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_notification_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notification_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `in_app_notify` tinyint DEFAULT '1' COMMENT '站内通知：0-关闭，1-开启',
  `email_notify` tinyint DEFAULT '1' COMMENT '邮件通知：0-关闭，1-开启',
  `sms_notify` tinyint DEFAULT '0' COMMENT '短信通知：0-关闭，1-开启',
  `progress_notify` tinyint DEFAULT '1' COMMENT '项目进度提醒：0-关闭，1-开启',
  `task_due_notify` tinyint DEFAULT '1' COMMENT '任务到期提醒：0-关闭，1-开启',
  `doc_update_notify` tinyint DEFAULT '0' COMMENT '文档更新提醒：0-关闭，1-开启',
  `archive_notify` tinyint DEFAULT '1' COMMENT '项目归档提醒：0-关闭，1-开启',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  CONSTRAINT `fk_nc_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户通知配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作用户名',
  `module` varchar(50) DEFAULT NULL COMMENT '操作模块',
  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型：login/create/update/delete/export等',
  `method` varchar(200) DEFAULT NULL COMMENT '请求方法',
  `request_url` varchar(255) DEFAULT NULL COMMENT '请求URL',
  `request_param` text COMMENT '请求参数',
  `response_result` text COMMENT '返回结果',
  `ip` varchar(50) DEFAULT NULL COMMENT '操作IP',
  `status` tinyint DEFAULT '1' COMMENT '操作状态：0-失败，1-成功',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误消息',
  `cost_time` bigint DEFAULT '0' COMMENT '耗时（毫秒）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_module` (`module`),
  KEY `idx_business_type` (`business_type`),
  KEY `idx_created_user` (`created_at`,`user_id`),
  KEY `idx_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=351 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `parent_id` bigint DEFAULT NULL COMMENT '父权限ID（NULL表示顶级）',
  `permission_name` varchar(64) NOT NULL COMMENT '权限名称',
  `permission_code` varchar(128) NOT NULL COMMENT '权限标识（如 system:user:add）',
  `permission_type` tinyint NOT NULL DEFAULT '1' COMMENT '权限类型：1菜单 2按钮 3接口',
  `path` varchar(128) DEFAULT NULL COMMENT '前端路由路径',
  `component` varchar(128) DEFAULT NULL COMMENT '前端组件路径',
  `icon` varchar(64) DEFAULT NULL COMMENT '菜单图标',
  `sort` int DEFAULT '0' COMMENT '排序',
  `is_visible` tinyint DEFAULT '1' COMMENT '是否可见：1是 0否',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用 0禁用',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_perm_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_permission` (`id`) ON DELETE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_position` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) NOT NULL COMMENT '岗位编码（如 pm/senior_engineer/engineer/junior_engineer/outsourcing/viewer）',
  `name` varchar(50) NOT NULL COMMENT '岗位名称',
  `level` int NOT NULL COMMENT '职级（1最高，9最低）',
  `description` varchar(200) DEFAULT NULL COMMENT '岗位描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用 0禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位职级表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(200) DEFAULT NULL COMMENT '角色描述',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_permission_id` (`permission_id`),
  CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=399 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `config_type` varchar(20) DEFAULT 'string' COMMENT '值类型：string/number/boolean/json',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名（登录账号）',
  `password` varchar(100) NOT NULL COMMENT '密码（加密存储，BCrypt）',
  `name` varchar(50) NOT NULL COMMENT '真实姓名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `gender` tinyint DEFAULT '0' COMMENT '性别：0-未知，1-男，2-女',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `bio` varchar(500) DEFAULT NULL COMMENT '个人简介',
  `two_factor_enabled` tinyint DEFAULT '0' COMMENT '双因素认证：0-未开启，1-已开启',
  `status` tinyint DEFAULT '1' COMMENT '账号状态：0-禁用，1-启用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `position_id` bigint DEFAULT NULL COMMENT '岗位ID（关联 sys_position.id）',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID（预留字段，当前不启用部门隔离）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_email` (`email`),
  KEY `idx_status` (`status`),
  KEY `idx_position_id` (`position_id`),
  KEY `idx_dept_id` (`dept_id`),
  CONSTRAINT `fk_user_dept` FOREIGN KEY (`dept_id`) REFERENCES `sys_department` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_position` FOREIGN KEY (`position_id`) REFERENCES `sys_position` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_role_id` (`role_id`),
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `v_active_assignment`;
/*!50001 DROP VIEW IF EXISTS `v_active_assignment`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_active_assignment` AS SELECT 
 1 AS `id`,
 1 AS `project_id`,
 1 AS `engineer_id`,
 1 AS `assigned_by`,
 1 AS `assigned_at`,
 1 AS `is_active`,
 1 AS `remark`,
 1 AS `permission_scope`,
 1 AS `expire_at`,
 1 AS `revoke_reason`,
 1 AS `revoked_at`,
 1 AS `project_name`,
 1 AS `project_code`,
 1 AS `project_status`,
 1 AS `engineer_name`,
 1 AS `engineer_phone`,
 1 AS `assigner_name`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `v_pending_progress_review`;
/*!50001 DROP VIEW IF EXISTS `v_pending_progress_review`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_pending_progress_review` AS SELECT 
 1 AS `log_id`,
 1 AS `task_id`,
 1 AS `project_id`,
 1 AS `progress`,
 1 AS `previous_progress`,
 1 AS `remark`,
 1 AS `images`,
 1 AS `report_time`,
 1 AS `task_name`,
 1 AS `task_status`,
 1 AS `task_progress`,
 1 AS `project_name`,
 1 AS `project_code`,
 1 AS `reporter_id`,
 1 AS `reporter_name`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `v_project_full`;
/*!50001 DROP VIEW IF EXISTS `v_project_full`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_project_full` AS SELECT 
 1 AS `id`,
 1 AS `project_code`,
 1 AS `name`,
 1 AS `status`,
 1 AS `priority`,
 1 AS `project_type`,
 1 AS `customer_id`,
 1 AS `manager_id`,
 1 AS `address`,
 1 AS `start_date`,
 1 AS `end_date`,
 1 AS `actual_start_date`,
 1 AS `actual_end_date`,
 1 AS `contract_amount`,
 1 AS `budget`,
 1 AS `progress`,
 1 AS `description`,
 1 AS `is_archived`,
 1 AS `archive_date`,
 1 AS `team_scope`,
 1 AS `created_by`,
 1 AS `created_at`,
 1 AS `updated_at`,
 1 AS `customer_name`,
 1 AS `customer_contact`,
 1 AS `customer_phone`,
 1 AS `manager_real_name`,
 1 AS `manager_phone`,
 1 AS `task_count`,
 1 AS `completed_task_count`,
 1 AS `doc_count`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `v_task_with_project`;
/*!50001 DROP VIEW IF EXISTS `v_task_with_project`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_task_with_project` AS SELECT 
 1 AS `id`,
 1 AS `project_id`,
 1 AS `name`,
 1 AS `parent_id`,
 1 AS `start_date`,
 1 AS `end_date`,
 1 AS `actual_start_date`,
 1 AS `actual_end_date`,
 1 AS `progress`,
 1 AS `status`,
 1 AS `assignee_id`,
 1 AS `is_milestone`,
 1 AS `is_key_task`,
 1 AS `sort`,
 1 AS `description`,
 1 AS `remark`,
 1 AS `created_by`,
 1 AS `created_at`,
 1 AS `updated_at`,
 1 AS `project_name`,
 1 AS `project_code`,
 1 AS `project_status`,
 1 AS `project_is_archived`,
 1 AS `assignee_real_name`,
 1 AS `assignee_phone`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `v_unread_notification_count`;
/*!50001 DROP VIEW IF EXISTS `v_unread_notification_count`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_unread_notification_count` AS SELECT 
 1 AS `user_id`,
 1 AS `unread_count`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `v_user_with_role_dept`;
/*!50001 DROP VIEW IF EXISTS `v_user_with_role_dept`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_user_with_role_dept` AS SELECT 
 1 AS `id`,
 1 AS `username`,
 1 AS `name`,
 1 AS `nickname`,
 1 AS `avatar`,
 1 AS `email`,
 1 AS `phone`,
 1 AS `gender`,
 1 AS `status`,
 1 AS `last_login_time`,
 1 AS `last_login_ip`,
 1 AS `dept_id`,
 1 AS `dept_name`,
 1 AS `position_id`,
 1 AS `position_name`,
 1 AS `position_level`,
 1 AS `role_codes`,
 1 AS `role_names`*/;
SET character_set_client = @saved_cs_client;
/*!50003 DROP FUNCTION IF EXISTS `fn_get_project_progress` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'STRICT_TRANS_TABLES,STRICT_ALL_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,TRADITIONAL,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE FUNCTION `fn_get_project_progress`(p_project_id BIGINT) RETURNS int
    READS SQL DATA
    DETERMINISTIC
RETURN (SELECT ROUND(IFNULL(SUM(CASE WHEN is_milestone = 1 THEN progress * 0.6 ELSE progress * 0.4 END) / NULLIF(SUM(CASE WHEN is_milestone = 1 THEN 0.6 ELSE 0.4 END), 0), 0)) FROM biz_task WHERE project_id = p_project_id) ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 DROP FUNCTION IF EXISTS `fn_user_has_permission` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_unicode_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'STRICT_TRANS_TABLES,STRICT_ALL_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,TRADITIONAL,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE FUNCTION `fn_user_has_permission`(p_user_id BIGINT, p_perm_code VARCHAR(100)) RETURNS tinyint(1)
    READS SQL DATA
    DETERMINISTIC
RETURN (SELECT COUNT(*) > 0 FROM sys_user_role ur JOIN sys_role_permission rp ON ur.role_id = rp.role_id JOIN sys_permission p ON rp.permission_id = p.id WHERE ur.user_id = p_user_id AND p.permission_code = p_perm_code COLLATE utf8mb4_0900_ai_ci) ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50001 DROP VIEW IF EXISTS `v_active_assignment`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_unicode_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY DEFINER */
/*!50001 VIEW `v_active_assignment` AS select `a`.`id` AS `id`,`a`.`project_id` AS `project_id`,`a`.`engineer_id` AS `engineer_id`,`a`.`assigned_by` AS `assigned_by`,`a`.`assigned_at` AS `assigned_at`,`a`.`is_active` AS `is_active`,`a`.`remark` AS `remark`,`a`.`permission_scope` AS `permission_scope`,`a`.`expire_at` AS `expire_at`,`a`.`revoke_reason` AS `revoke_reason`,`a`.`revoked_at` AS `revoked_at`,`p`.`name` AS `project_name`,`p`.`project_code` AS `project_code`,`p`.`status` AS `project_status`,`u`.`name` AS `engineer_name`,`u`.`phone` AS `engineer_phone`,`ua`.`name` AS `assigner_name` from (((`biz_project_assignment` `a` left join `biz_project` `p` on((`a`.`project_id` = `p`.`id`))) left join `sys_user` `u` on((`a`.`engineer_id` = `u`.`id`))) left join `sys_user` `ua` on((`a`.`assigned_by` = `ua`.`id`))) where (`a`.`is_active` = 1) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!50001 DROP VIEW IF EXISTS `v_pending_progress_review`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_unicode_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY DEFINER */
/*!50001 VIEW `v_pending_progress_review` AS select `l`.`id` AS `log_id`,`l`.`task_id` AS `task_id`,`l`.`project_id` AS `project_id`,`l`.`progress` AS `progress`,`l`.`previous_progress` AS `previous_progress`,`l`.`remark` AS `remark`,`l`.`images` AS `images`,`l`.`report_time` AS `report_time`,`t`.`name` AS `task_name`,`t`.`status` AS `task_status`,`t`.`progress` AS `task_progress`,`p`.`name` AS `project_name`,`p`.`project_code` AS `project_code`,`u`.`id` AS `reporter_id`,`u`.`name` AS `reporter_name` from (((`biz_task_progress_log` `l` join `biz_task` `t` on((`l`.`task_id` = `t`.`id`))) join `biz_project` `p` on((`l`.`project_id` = `p`.`id`))) join `sys_user` `u` on((`l`.`reporter_id` = `u`.`id`))) where (`l`.`review_status` = 'pending') */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!50001 DROP VIEW IF EXISTS `v_project_full`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_unicode_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY DEFINER */
/*!50001 VIEW `v_project_full` AS select `p`.`id` AS `id`,`p`.`project_code` AS `project_code`,`p`.`name` AS `name`,`p`.`status` AS `status`,`p`.`priority` AS `priority`,`p`.`project_type` AS `project_type`,`p`.`customer_id` AS `customer_id`,`p`.`manager_id` AS `manager_id`,`p`.`address` AS `address`,`p`.`start_date` AS `start_date`,`p`.`end_date` AS `end_date`,`p`.`actual_start_date` AS `actual_start_date`,`p`.`actual_end_date` AS `actual_end_date`,`p`.`contract_amount` AS `contract_amount`,`p`.`budget` AS `budget`,`p`.`progress` AS `progress`,`p`.`description` AS `description`,`p`.`is_archived` AS `is_archived`,`p`.`archive_date` AS `archive_date`,`p`.`team_scope` AS `team_scope`,`p`.`created_by` AS `created_by`,`p`.`created_at` AS `created_at`,`p`.`updated_at` AS `updated_at`,`c`.`name` AS `customer_name`,`c`.`contact_person` AS `customer_contact`,`c`.`contact_phone` AS `customer_phone`,`u`.`name` AS `manager_real_name`,`u`.`phone` AS `manager_phone`,(select count(0) from `biz_task` `t` where (`t`.`project_id` = `p`.`id`)) AS `task_count`,(select count(0) from `biz_task` `t` where ((`t`.`project_id` = `p`.`id`) and (`t`.`status` = '已完成'))) AS `completed_task_count`,(select count(0) from `biz_document` `d` where (`d`.`project_id` = `p`.`id`)) AS `doc_count` from ((`biz_project` `p` left join `biz_customer` `c` on((`p`.`customer_id` = `c`.`id`))) left join `sys_user` `u` on((`p`.`manager_id` = `u`.`id`))) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!50001 DROP VIEW IF EXISTS `v_task_with_project`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_unicode_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY DEFINER */
/*!50001 VIEW `v_task_with_project` AS select `t`.`id` AS `id`,`t`.`project_id` AS `project_id`,`t`.`name` AS `name`,`t`.`parent_id` AS `parent_id`,`t`.`start_date` AS `start_date`,`t`.`end_date` AS `end_date`,`t`.`actual_start_date` AS `actual_start_date`,`t`.`actual_end_date` AS `actual_end_date`,`t`.`progress` AS `progress`,`t`.`status` AS `status`,`t`.`assignee_id` AS `assignee_id`,`t`.`is_milestone` AS `is_milestone`,`t`.`is_key_task` AS `is_key_task`,`t`.`sort` AS `sort`,`t`.`description` AS `description`,`t`.`remark` AS `remark`,`t`.`created_by` AS `created_by`,`t`.`created_at` AS `created_at`,`t`.`updated_at` AS `updated_at`,`p`.`name` AS `project_name`,`p`.`project_code` AS `project_code`,`p`.`status` AS `project_status`,`p`.`is_archived` AS `project_is_archived`,`u`.`name` AS `assignee_real_name`,`u`.`phone` AS `assignee_phone` from ((`biz_task` `t` left join `biz_project` `p` on((`t`.`project_id` = `p`.`id`))) left join `sys_user` `u` on((`t`.`assignee_id` = `u`.`id`))) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!50001 DROP VIEW IF EXISTS `v_unread_notification_count`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_unicode_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY DEFINER */
/*!50001 VIEW `v_unread_notification_count` AS select `sys_notification`.`user_id` AS `user_id`,count(0) AS `unread_count` from `sys_notification` where (`sys_notification`.`is_read` = 0) group by `sys_notification`.`user_id` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!50001 DROP VIEW IF EXISTS `v_user_with_role_dept`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_unicode_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY DEFINER */
/*!50001 VIEW `v_user_with_role_dept` AS select `u`.`id` AS `id`,`u`.`username` AS `username`,`u`.`name` AS `name`,`u`.`nickname` AS `nickname`,`u`.`avatar` AS `avatar`,`u`.`email` AS `email`,`u`.`phone` AS `phone`,`u`.`gender` AS `gender`,`u`.`status` AS `status`,`u`.`last_login_time` AS `last_login_time`,`u`.`last_login_ip` AS `last_login_ip`,`d`.`id` AS `dept_id`,`d`.`name` AS `dept_name`,`p`.`id` AS `position_id`,`p`.`name` AS `position_name`,`p`.`level` AS `position_level`,group_concat(distinct `r`.`role_code` separator ',') AS `role_codes`,group_concat(distinct `r`.`role_name` separator ',') AS `role_names` from ((((`sys_user` `u` left join `sys_department` `d` on((`u`.`dept_id` = `d`.`id`))) left join `sys_position` `p` on((`u`.`position_id` = `p`.`id`))) left join `sys_user_role` `ur` on((`u`.`id` = `ur`.`user_id`))) left join `sys_role` `r` on((`ur`.`role_id` = `r`.`id`))) group by `u`.`id` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

