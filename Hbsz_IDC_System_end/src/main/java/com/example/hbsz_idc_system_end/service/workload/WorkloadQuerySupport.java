package com.example.hbsz_idc_system_end.service.workload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.common.exception.BusinessException;
import com.example.hbsz_idc_system_end.common.result.ResultCode;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.entity.BizProjectTeam;
import com.example.hbsz_idc_system_end.entity.BizWorkloadItem;
import com.example.hbsz_idc_system_end.entity.SysUser;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectTeamMapper;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadItemMapper;
import com.example.hbsz_idc_system_end.mapper.BizWorkloadRecordMapper;
import com.example.hbsz_idc_system_end.mapper.SysUserMapper;
import com.example.hbsz_idc_system_end.service.IProjectAuthorizationService;
import com.example.hbsz_idc_system_end.vo.WorkloadItemVO;
import com.example.hbsz_idc_system_end.vo.WorkloadWorkerVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作量模块共享查询/校验支撑（无状态协作类）
 * <p>
 * 由原 WorkloadServiceImpl 的内部工具方法拆出，供清单维护、统计矩阵、
 * 填报、审核、Excel 导出各协作类共用，避免多份拷贝产生行为漂移。
 * 审核状态常量收敛于此处，作为全模块唯一定义点。
 *
 * @author hbsz
 */
@Component
public class WorkloadQuerySupport {

    /** 审核状态常量：待审核 */
    public static final String REVIEW_PENDING = "pending";
    /** 审核状态常量：已通过 */
    public static final String REVIEW_APPROVED = "approved";
    /** 审核状态常量：已驳回 */
    public static final String REVIEW_REJECTED = "rejected";

    private final BizWorkloadItemMapper itemMapper;
    private final BizWorkloadRecordMapper recordMapper;
    private final BizProjectMapper projectMapper;
    private final BizProjectTeamMapper projectTeamMapper;
    private final SysUserMapper userMapper;
    private final IProjectAuthorizationService projectAuthorizationService;

    public WorkloadQuerySupport(BizWorkloadItemMapper itemMapper,
                                BizWorkloadRecordMapper recordMapper,
                                BizProjectMapper projectMapper,
                                BizProjectTeamMapper projectTeamMapper,
                                SysUserMapper userMapper,
                                IProjectAuthorizationService projectAuthorizationService) {
        this.itemMapper = itemMapper;
        this.recordMapper = recordMapper;
        this.projectMapper = projectMapper;
        this.projectTeamMapper = projectTeamMapper;
        this.userMapper = userMapper;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    /** IDOR 防护：校验当前用户对项目的访问权限 */
    public void checkProjectAccess(Long projectId) {
        if (projectId == null || !projectAuthorizationService.canAccessProject(projectId)) {
            throw new BusinessException(ResultCode.PROJECT_NOT_AUTHORIZED, "无权访问该项目的工作量数据");
        }
    }

    /**
     * 查询项目组在组成员（含组长），组长置前、其余按姓名排序。
     * <p>
     * 兼容历史填报矩阵中的人员（userId 为 null 的自由文本记录）不在此列，
     * 仅用于"可填报人员"下拉来源。
     */
    public List<WorkloadWorkerVO> listActiveTeamWorkers(Long projectId) {
        List<BizProjectTeam> teams = projectTeamMapper.selectList(new LambdaQueryWrapper<BizProjectTeam>()
                .eq(BizProjectTeam::getProjectId, projectId)
                .eq(BizProjectTeam::getIsActive, 1));
        if (teams.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        teams.forEach(t -> userIds.add(t.getUserId()));
        Map<Long, String> nameMap = new HashMap<>();
        for (SysUser user : userMapper.selectBatchIds(userIds)) {
            nameMap.put(user.getId(), StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername());
        }
        Map<Long, String> roleMap = new HashMap<>();
        teams.forEach(t -> roleMap.put(t.getUserId(), t.getRoleInTeam()));
        List<WorkloadWorkerVO> result = new ArrayList<>(teams.size());
        for (BizProjectTeam team : teams) {
            WorkloadWorkerVO vo = new WorkloadWorkerVO();
            vo.setUserId(team.getUserId());
            vo.setName(nameMap.getOrDefault(team.getUserId(), "用户" + team.getUserId()));
            vo.setRoleInTeam(team.getRoleInTeam());
            result.add(vo);
        }
        return sortWorkers(result);
    }

    /** 项目组在组成员 userId → 姓名映射（用于填报人员校验与姓名快照） */
    public Map<Long, String> mapActiveTeamMemberNames(Long projectId) {
        Map<Long, String> result = new HashMap<>();
        for (WorkloadWorkerVO worker : listActiveTeamWorkers(projectId)) {
            result.put(worker.getUserId(), worker.getName());
        }
        return result;
    }

    /** 人员排序：组长置前，其余按姓名自然排序 */
    public List<WorkloadWorkerVO> sortWorkers(List<WorkloadWorkerVO> workers) {
        workers.sort(Comparator
                .comparing((WorkloadWorkerVO w) -> "leader".equals(w.getRoleInTeam()) ? 0 : 1)
                .thenComparing(WorkloadWorkerVO::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        return workers;
    }

    /** 校验项目存在 */
    public void checkProjectExists(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
    }

    /** 加载清单项（按 sortOrder 升序、id 升序兜底，保证行序稳定） */
    public List<BizWorkloadItem> listItemsByProject(Long projectId) {
        return itemMapper.selectList(new LambdaQueryWrapper<BizWorkloadItem>()
                .eq(BizWorkloadItem::getProjectId, projectId)
                .orderByAsc(BizWorkloadItem::getSortOrder)
                .orderByAsc(BizWorkloadItem::getId));
    }

    /** 按清单项聚合完成量：itemId → {completed, todayQuantity} */
    public Map<Long, Map<String, Object>> aggregateByItem(Long projectId, LocalDate statisticsDate) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : recordMapper.selectItemAggregates(projectId, statisticsDate)) {
            result.put(toLong(row.get("itemId")), row);
        }
        return result;
    }

    /** 实体 → VO（聚合行可为 null，此时统计列置 0） */
    public WorkloadItemVO toItemVO(BizWorkloadItem item, Map<String, Object> aggregate) {
        WorkloadItemVO vo = new WorkloadItemVO();
        vo.setId(item.getId());
        vo.setProjectId(item.getProjectId());
        vo.setCategory(item.getCategory());
        vo.setWorkScope(item.getWorkScope());
        vo.setRatedWorkload(item.getRatedWorkload());
        vo.setUnit(item.getUnit());
        vo.setPerformanceScore(item.getPerformanceScore());
        vo.setTotalQuantity(item.getTotalQuantity());
        vo.setRemark(item.getRemark());
        vo.setSortOrder(item.getSortOrder());
        BigDecimal completed = aggregate != null ? toBigDecimal(aggregate.get("completed")) : BigDecimal.ZERO;
        BigDecimal today = aggregate != null ? toBigDecimal(aggregate.get("todayQuantity")) : BigDecimal.ZERO;
        vo.setCompletedQuantity(scale(completed, 2));
        vo.setTodayQuantity(scale(today, 2));
        // 剩余 = 总量 - 已完成；未设置总量时返回 null（前端展示 "-"）
        if (item.getTotalQuantity() != null) {
            vo.setRemainingQuantity(scale(item.getTotalQuantity().subtract(completed), 2));
        }
        return vo;
    }

    /** 额定工作量取值（null/非法时按 1，避免除零） */
    public BigDecimal ratedOf(BizWorkloadItem item) {
        BigDecimal rated = item.getRatedWorkload();
        return (rated == null || rated.compareTo(BigDecimal.ZERO) <= 0) ? BigDecimal.ONE : rated;
    }

    /** 项目名称（不存在时返回占位符） */
    public String projectName(Long projectId) {
        BizProject project = projectMapper.selectById(projectId);
        return project != null ? project.getName() : String.valueOf(projectId);
    }

    /** 数值精度统一（null 返回 0） */
    public BigDecimal scale(BigDecimal value, int scale) {
        return (value != null ? value : BigDecimal.ZERO).setScale(scale, RoundingMode.HALF_UP);
    }

    /** Map 值转 Long */
    public Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Map 值转 BigDecimal */
    public BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
