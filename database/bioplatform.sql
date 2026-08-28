-- ============================================================
-- BioPlatform - Bioinformatics Cloud Platform Database Schema
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. users
-- ============================================================
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `username`       VARCHAR(64)  NOT NULL,
    `email`          VARCHAR(128) NOT NULL,
    `password`       VARCHAR(255) NOT NULL,
    `nick_name`      VARCHAR(64)  DEFAULT NULL,
    `avatar_url`     VARCHAR(512) DEFAULT NULL,
    `phone`          VARCHAR(20)  DEFAULT NULL,
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '0=disabled 1=active',
    `upload_quota`   BIGINT       NOT NULL DEFAULT 10737418240 COMMENT 'Upload quota in bytes, default 10GB',
    `last_login_at`  DATETIME(6)  DEFAULT NULL,
    `login_attempts` INT          NOT NULL DEFAULT 0,
    `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`),
    UNIQUE KEY `uk_users_email` (`email`),
    INDEX `idx_users_status` (`status`),
    INDEX `idx_users_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User accounts';

-- ============================================================
-- 2. roles
-- ============================================================
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
    `id`        BIGINT      NOT NULL AUTO_INCREMENT,
    `role_name` VARCHAR(64) NOT NULL COMMENT 'ROLE_USER / ROLE_ADMIN etc.',
    `role_desc` VARCHAR(255) DEFAULT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_roles_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Roles';

-- ============================================================
-- 3. permissions
-- ============================================================
DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(64)  NOT NULL,
    `permission`  VARCHAR(255) NOT NULL COMMENT 'Permission identifier, e.g. user:list',
    `type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=menu 2=button 3=api',
    `parent_id`   BIGINT       DEFAULT 0 COMMENT '0=root',
    `path`        VARCHAR(255) DEFAULT NULL COMMENT 'Frontend route or API path',
    `order_num`   INT          NOT NULL DEFAULT 0,
    `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permissions_perm` (`permission`),
    INDEX `idx_permissions_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Permission tree';

-- ============================================================
-- 4. user_roles  (M : N)
-- ============================================================
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    INDEX `idx_ur_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User <-> Role mapping';

-- ============================================================
-- 5. role_permissions  (M : N)
-- ============================================================
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions` (
    `role_id`       BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    PRIMARY KEY (`role_id`, `permission_id`),
    INDEX `idx_rp_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role <-> Permission mapping';

-- ============================================================
-- 6. projects
-- ============================================================
DROP TABLE IF EXISTS `projects`;
CREATE TABLE `projects` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(128) NOT NULL,
    `description`    TEXT          DEFAULT NULL,
    `organism`       VARCHAR(128) DEFAULT NULL COMMENT 'e.g. Homo sapiens, Arabidopsis thaliana',
    `genome_version` VARCHAR(64)  DEFAULT NULL COMMENT 'e.g. GRCh38, TAIR10',
    `owner_id`       BIGINT       NOT NULL,
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '0=archived 1=active',
    `private`        TINYINT      NOT NULL DEFAULT 1 COMMENT '0=public 1=private',
    `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_projects_owner` (`owner_id`),
    INDEX `idx_projects_status` (`status`),
    INDEX `idx_projects_organism` (`organism`),
    CONSTRAINT `fk_projects_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Research projects';

-- ============================================================
-- 7. workflow_templates
-- ============================================================
DROP TABLE IF EXISTS `workflow_templates`;
CREATE TABLE `workflow_templates` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `name`             VARCHAR(128)  NOT NULL COMMENT '模板名称，如 RNAseq、STAR',
    `description`      TEXT          DEFAULT NULL,
    `type`             VARCHAR(16)   NOT NULL COMMENT 'task / pipeline',
    `category`         VARCHAR(64)   DEFAULT NULL COMMENT '分组：转录组、变异检测、表观遗传学等',
    `config_template`  JSON          NOT NULL COMMENT '默认配置 JSON（来自 Omics/config/*.json）',
    `schema_json`      JSON          NOT NULL COMMENT '表单 schema（来自 Omics/config/*.schema.json）',
    `snakemake_path`   VARCHAR(255)  NOT NULL COMMENT '相对于 Omics 仓库的 .smk 路径',
    `icon`             VARCHAR(64)   DEFAULT NULL,
    `sort_order`       INT           DEFAULT 0,
    `enabled`          TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_wt_type` (`type`),
    INDEX `idx_wt_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workflow templates';

-- ============================================================
-- 8. pipelines
-- ============================================================
DROP TABLE IF EXISTS `pipelines`;
CREATE TABLE `pipelines` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(128)  NOT NULL,
    `type`         VARCHAR(16)   NOT NULL DEFAULT 'pipeline' COMMENT 'task / pipeline',
    `template_id`  BIGINT        DEFAULT NULL COMMENT '关联 workflow_templates.id',
    `project_id`   BIGINT        DEFAULT NULL COMMENT '关联 projects.id',
    `meta_content` TEXT          DEFAULT NULL COMMENT '元数据内容',
    `meta_type`    VARCHAR(32)   DEFAULT NULL COMMENT '元数据类型',
    `extra_params` JSON          DEFAULT NULL COMMENT '额外参数',
    `description`  TEXT          DEFAULT NULL,
    `category`     VARCHAR(64)   DEFAULT NULL COMMENT 'e.g. QC, Alignment, Assembly, Annotation',
    `config_json`  JSON          DEFAULT NULL COMMENT '用户填写的实际配置',
    `docker_image` VARCHAR(255)  DEFAULT NULL,
    `timeout`      INT           DEFAULT 3600 COMMENT 'Seconds',
    `owner_id`     BIGINT        NOT NULL,
    `created_at`   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_pipelines_owner` (`owner_id`),
    INDEX `idx_pipelines_category` (`category`),
    INDEX `idx_pipelines_type` (`type`),
    CONSTRAINT `fk_pipelines_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_pipelines_template` FOREIGN KEY (`template_id`) REFERENCES `workflow_templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bioinformatics pipelines';

-- ============================================================
-- 9. pipeline_executions
-- ============================================================
DROP TABLE IF EXISTS `pipeline_executions`;
CREATE TABLE `pipeline_executions` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `pipeline_id`  BIGINT       NOT NULL,
    `project_id`   BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    `input_params` JSON         DEFAULT NULL,
    `output_path`  VARCHAR(512) DEFAULT NULL,
    `error_log`    TEXT         DEFAULT NULL,
    `worker_id`    VARCHAR(64)  DEFAULT NULL COMMENT '执行该任务的Worker ID',
    `worker_url`   VARCHAR(256) DEFAULT NULL COMMENT '执行该任务的Worker URL',
    `started_at`   DATETIME(6)  DEFAULT NULL,
    `finished_at`  DATETIME(6)  DEFAULT NULL,
    `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_pe_pipeline` (`pipeline_id`),
    INDEX `idx_pe_project` (`project_id`),
    INDEX `idx_pe_user` (`user_id`),
    INDEX `idx_pe_status` (`status`),
    INDEX `idx_pe_created` (`created_at`),
    CONSTRAINT `fk_pe_pipeline` FOREIGN KEY (`pipeline_id`) REFERENCES `pipelines` (`id`),
    CONSTRAINT `fk_pe_project`  FOREIGN KEY (`project_id`)  REFERENCES `projects` (`id`),
    CONSTRAINT `fk_pe_user`     FOREIGN KEY (`user_id`)     REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pipeline execution records';

-- ============================================================
-- 9. data_files
-- ============================================================
DROP TABLE IF EXISTS `data_files`;
CREATE TABLE `data_files` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(255) NOT NULL,
    `path`           VARCHAR(512) NOT NULL,
    `file_type`      VARCHAR(32)  DEFAULT NULL COMMENT 'e.g. fastq, bam, vcf, gff',
    `file_size`      BIGINT       DEFAULT 0 COMMENT 'Bytes',
    `organism`       VARCHAR(128) DEFAULT NULL,
    `genome_version` VARCHAR(64)  DEFAULT NULL,
    `project_id`     BIGINT       DEFAULT NULL,
    `uploaded_by`    BIGINT       NOT NULL,
    `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_df_project` (`project_id`),
    INDEX `idx_df_uploader` (`uploaded_by`),
    INDEX `idx_df_type` (`file_type`),
    INDEX `idx_df_organism` (`organism`),
    CONSTRAINT `fk_df_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`),
    CONSTRAINT `fk_df_user`    FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Uploaded data files';

-- ============================================================
-- 10. agent_conversations
-- ============================================================
DROP TABLE IF EXISTS `agent_conversations`;
CREATE TABLE `agent_conversations` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL,
    `project_id`  BIGINT       DEFAULT NULL,
    `title`       VARCHAR(255) DEFAULT NULL,
    `model_name`  VARCHAR(64)  DEFAULT NULL COMMENT 'e.g. gpt-4, deepseek-v2',
    `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_ac_user` (`user_id`),
    INDEX `idx_ac_project` (`project_id`),
    INDEX `idx_ac_updated` (`updated_at`),
    CONSTRAINT `fk_ac_user`    FOREIGN KEY (`user_id`)    REFERENCES `users` (`id`),
    CONSTRAINT `fk_ac_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI agent conversation sessions';

-- ============================================================
-- 11. agent_messages
-- ============================================================
DROP TABLE IF EXISTS `agent_messages`;
CREATE TABLE `agent_messages` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT   NOT NULL,
    `role`            VARCHAR(32) NOT NULL COMMENT 'user/assistant/system/tool',
    `content`         TEXT     DEFAULT NULL,
    `tool_calls_json` JSON     DEFAULT NULL,
    `created_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_am_conversation` (`conversation_id`),
    INDEX `idx_am_created` (`created_at`),
    CONSTRAINT `fk_am_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `agent_conversations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent chat messages';

-- ============================================================
-- 12. agent_tools
-- ============================================================
DROP TABLE IF EXISTS `agent_tools`;
CREATE TABLE `agent_tools` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(64)  NOT NULL,
    `description` VARCHAR(512) DEFAULT NULL,
    `category`    VARCHAR(64)  DEFAULT NULL COMMENT 'e.g. bioinformatics, data, system',
    `enabled`     TINYINT      NOT NULL DEFAULT 1,
    `config_json` JSON         DEFAULT NULL,
    `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_at_name` (`name`),
    INDEX `idx_at_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Registered AI agent tools';

-- ============================================================
-- 13. system_configs
-- ============================================================
DROP TABLE IF EXISTS `system_configs`;
CREATE TABLE `system_configs` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `config_key`   VARCHAR(128) NOT NULL,
    `config_value` TEXT         DEFAULT NULL,
    `config_desc`  VARCHAR(512) DEFAULT NULL,
    `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sc_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System configuration key-value store';

-- ============================================================
-- 14. operation_logs
-- ============================================================
DROP TABLE IF EXISTS `operation_logs`;
CREATE TABLE `operation_logs` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       DEFAULT NULL,
    `operation`  VARCHAR(128) NOT NULL COMMENT 'e.g. user.login, pipeline.create',
    `method`     VARCHAR(500) DEFAULT NULL COMMENT 'HTTP method + class.method',
    `params`     TEXT         DEFAULT NULL COMMENT 'Request parameters JSON',
    `result`     TEXT         DEFAULT NULL COMMENT 'Result or error summary',
    `ip`         VARCHAR(64)  DEFAULT NULL,
    `created_at` DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_ol_user` (`user_id`),
    INDEX `idx_ol_operation` (`operation`),
    INDEX `idx_ol_created` (`created_at`),
    CONSTRAINT `fk_ol_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit / operation logs';

-- ============================================================
-- 15. feedback_sessions
-- ============================================================
DROP TABLE IF EXISTS `feedback_sessions`;
CREATE TABLE `feedback_sessions` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       DEFAULT NULL COMMENT '用户ID',
    `user_name`   VARCHAR(64)  DEFAULT '匿名用户',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=open, 1=closed',
    `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_fs_user` (`user_id`),
    INDEX `idx_fs_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Feedback chat sessions';

-- ============================================================
-- 16. feedback_messages
-- ============================================================
DROP TABLE IF EXISTS `feedback_messages`;
CREATE TABLE `feedback_messages` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`   BIGINT       NOT NULL,
    `sender_type`  VARCHAR(16)  NOT NULL COMMENT 'user/admin/system',
    `sender_name`  VARCHAR(64)  DEFAULT NULL,
    `content`      TEXT         NOT NULL,
    `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_fm_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Feedback chat messages';

-- ============================================================
-- 17. compute_nodes
-- ============================================================
DROP TABLE IF EXISTS `compute_nodes`;
CREATE TABLE `compute_nodes` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `node_id`     VARCHAR(64)  NOT NULL COMMENT '节点唯一标识',
    `hostname`    VARCHAR(128) DEFAULT NULL,
    `url`         VARCHAR(256) NOT NULL COMMENT 'Worker地址',
    `cpu_cores`   INT          DEFAULT 0,
    `memory_mb`   BIGINT       DEFAULT 0,
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    `healthy`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0=离线 1=在线',
    `last_heartbeat` DATETIME(6) DEFAULT NULL,
    `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cn_node_id` (`node_id`),
    INDEX `idx_cn_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Compute nodes for distributed execution';

-- ============================================================
-- ============================================================
--  INIT DATA
-- ============================================================
-- ============================================================

-- ----------------------------
-- Default roles
-- ----------------------------
INSERT INTO `roles` (`id`, `role_name`, `role_desc`) VALUES
(1, 'ROLE_USER',  'Normal user'),
(2, 'ROLE_ADMIN', 'System administrator');

-- ----------------------------
-- Permission tree
-- ----------------------------
-- Level-0  (id = parent_id = 0)
-- Level-1  children
-- Level-2  leaf buttons / api
-- ----------------------------
INSERT INTO `permissions` (`id`, `name`, `permission`, `type`, `parent_id`, `path`, `order_num`) VALUES
-- 1. System management (menu)
( 1, 'System',          'system',                 1, 0, '/system',            1),
-- 1.1 User management
( 2, 'User Management', 'system:user:list',       1, 1, '/system/user',       1),
( 3, 'Create User',     'system:user:create',     3, 2, NULL,                  1),
( 4, 'Edit User',       'system:user:edit',       3, 2, NULL,                  2),
( 5, 'Delete User',     'system:user:delete',     3, 2, NULL,                  3),
( 6, 'View User',       'system:user:view',       3, 2, NULL,                  4),
-- 1.2 Role management
( 7, 'Role Management', 'system:role:list',       1, 1, '/system/role',       2),
( 8, 'Create Role',     'system:role:create',     3, 7, NULL,                  1),
( 9, 'Edit Role',       'system:role:edit',       3, 7, NULL,                  2),
(10, 'Delete Role',     'system:role:delete',     3, 7, NULL,                  3),
-- 1.3 Permission management
(11, 'Permission Mgmt', 'system:permission:list', 1, 1, '/system/permission', 3),
-- 1.4 System config
(12, 'System Config',   'system:config:list',     1, 1, '/system/config',     4),
(13, 'Edit Config',     'system:config:edit',     3, 12, NULL,                 1),

-- 2. Project management (menu)
(20, 'Projects',        'project',                1, 0, '/project',            2),
(21, 'Project List',    'project:list',           1, 20, '/project/list',      1),
(22, 'Create Project',  'project:create',         3, 21, NULL,                  1),
(23, 'Edit Project',    'project:edit',           3, 21, NULL,                  2),
(24, 'Delete Project',  'project:delete',         3, 21, NULL,                  3),

-- 3. Pipeline management (menu)
(30, 'Pipelines',       'pipeline',               1, 0, '/pipeline',           3),
(31, 'Pipeline List',   'pipeline:list',          1, 30, '/pipeline/list',     1),
(32, 'Create Pipeline', 'pipeline:create',        3, 31, NULL,                  1),
(33, 'Edit Pipeline',   'pipeline:edit',          3, 31, NULL,                  2),
(34, 'Delete Pipeline', 'pipeline:delete',        3, 31, NULL,                  3),
(35, 'Run Pipeline',    'pipeline:run',           3, 31, NULL,                  4),

-- 4. Data management (menu)
(40, 'Data Management', 'data',                   1, 0, '/data',               4),
(41, 'File List',       'data:list',              1, 40, '/data/list',         1),
(42, 'Upload File',     'data:upload',            3, 41, NULL,                  1),
(43, 'Download File',   'data:download',          3, 41, NULL,                  2),
(44, 'Delete File',     'data:delete',            3, 41, NULL,                  3),

-- 5. AI Agent (menu)
(50, 'AI Agent',        'agent',                  1, 0, '/agent',              5),
(51, 'Chat',            'agent:chat',             1, 50, '/agent/chat',        1),
(52, 'Tool Management', 'agent:tool:list',        1, 50, '/agent/tool',        2),

-- 6. Logs (menu)
(60, 'Operation Logs',  'log:operation:list',     1, 0, '/log/operation',      6);

-- ----------------------------
-- Admin role -> all permissions
-- ----------------------------
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT 2, `id` FROM `permissions`;

-- ----------------------------
-- USER role -> read-only subset
-- ----------------------------
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
VALUES
(1, 21),  -- project list
(1, 22),  -- create project
(1, 23),  -- edit project
(1, 31),  -- pipeline list
(1, 35),  -- run pipeline
(1, 41),  -- data list
(1, 42),  -- upload
(1, 43),  -- download
(1, 51),  -- agent chat
(1, 52);  -- agent tools
INSERT INTO `role_permissions` (`role_id`, `permission_id`)
VALUES
(1, 20),
(1, 30),
(1, 40),
(1, 50);

-- ----------------------------
-- Admin user  (password = placeholder bcrypt hash)
-- ----------------------------
INSERT INTO `users` (`id`, `username`, `email`, `password`, `nick_name`, `status`)
VALUES (1, 'admin', 'admin@bioplatform.local',
        '$2b$10$kbqrtb6ugJRX3s8G58SPpetXkBk.Jy9IEfQRFWF/hv9LKd4.ljrcK',
        'Administrator', 1);

INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES (1, 2);

-- ----------------------------
-- Default system configurations
-- ----------------------------
INSERT INTO `system_configs` (`config_key`, `config_value`, `config_desc`) VALUES
('site_name',    'BioPlatform',                   'Display name of the platform'),
('site_description', '一站式生物信息学分析云平台',    '平台描述信息'),
('site_contact_email', 'support@bioplatform.com',   '联系邮箱'),
('site_github_url', 'https://github.com/bioplatform', 'GitHub主页地址'),
('llm_api_key',  '«redacted:sk-…»',          'LLM provider API key'),
('llm_model',    'deepseek-chat',                 'Default LLM model name'),
('llm_base_url', 'https://api.deepseek.com/v1',  'LLM API base URL'),
('upload_max_size', '1073741824',                 'Max upload size in bytes (1 GB)');

-- ----------------------------
-- Default agent tools
-- ----------------------------
INSERT INTO `agent_tools` (`name`, `description`, `category`, `enabled`) VALUES
('blast',      'Run BLAST sequence alignment',     'bioinformatics', 1),
('fastqc',     'Quality control for FASTQ files',  'bioinformatics', 1),
('samtools',   'SAM/BAM manipulation toolkit',     'bioinformatics', 1),
('bedtools',   'Genomic intervals manipulation',    'bioinformatics', 1),
('web_search', 'Search the web for information',    'data',           1),
('file_read',  'Read and parse data files',          'data',           1);

SET FOREIGN_KEY_CHECKS = 1;
