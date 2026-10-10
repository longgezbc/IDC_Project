package com.example.hbsz_app_android.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 项目授权与转移 DTO —— 字段与后端 ProjectAuthorizationController 的
 * ProjectTransferDTO / ProjectAssignmentVO / TransferLogVO 一一对应（camelCase）。
 *
 * 同步背景（2026-08 后端重设计）：项目转移支持三种模式——
 * 负责人移交（toManagerId：manager_id 变更 + 审批待办实时流向新负责人）、
 * 工程师替换（toEngineerId：撤销全部现有授权并授权目标工程师）、
 * 组合转移（两者同时传入）；至少传一项由后端校验。
 */

/**
 * POST /projects/{projectId}/authorization/transfer
 * POST /projects/{projectId}/authorization 请求体。
 * Json 配置 explicitNulls=false：null 字段不序列化，
 * 与后端"至少传一个"的语义天然对齐。
 */
@Serializable
data class ProjectTransferRequest(
    /** 目标工程师ID（替换/新增授权模式必传） */
    val toEngineerId: Long? = null,
    /** 目标负责人ID（负责人移交模式必传） */
    val toManagerId: Long? = null,
    /** 操作备注（写入审计日志） */
    val remark: String? = null
)

/**
 * GET /projects/authorization/engineers、GET /projects/authorization/managers 响应项。
 * 后端为防敏感字段泄露仅返回精简 Map（id/username/name/phone）。
 */
@Serializable
data class ActiveUserDto(
    val id: Long,
    val username: String? = null,
    val name: String? = null,
    val phone: String? = null
)

/** GET /projects/{projectId}/authorization 响应项（有效授权工程师） */
@Serializable
data class ProjectAssignmentDto(
    val id: Long? = null,
    val projectId: Long? = null,
    val engineerId: Long? = null,
    val engineerName: String? = null,
    val engineerUsername: String? = null,
    val engineerPhone: String? = null,
    val assignedBy: Long? = null,
    val assignedByName: String? = null,
    val assignedAt: String? = null,
    val isActive: Int? = null,
    val remark: String? = null,
    /** 授权范围：read_only / read_write / review */
    val permissionScope: String? = null,
    /** 授权过期时间（null=永久） */
    val expireAt: String? = null
)

/**
 * GET /projects/authorization/logs 响应项（转移/授权审计日志）。
 * 注意：operatedBy 可能为 null（系统自动操作，如授权过期定时回收，
 * 此时 operatorName 为"系统定时任务"，见后端迁移 V20260820.1）。
 */
@Serializable
data class TransferLogDto(
    val id: Long? = null,
    val projectId: Long? = null,
    val projectName: String? = null,
    /** 操作类型：transfer/transfer_leader/assign/revoke/auth_request/approve/reject/extend/team_add/team_remove */
    val actionType: String? = null,
    val fromEngineerId: Long? = null,
    val fromEngineerName: String? = null,
    val toEngineerId: Long? = null,
    val toEngineerName: String? = null,
    val operatedBy: Long? = null,
    val operatorName: String? = null,
    val operatedAt: String? = null,
    val remark: String? = null
)
