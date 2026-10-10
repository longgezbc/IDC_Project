package com.example.hbsz_idc_system_end.controller;

import com.example.hbsz_idc_system_end.common.annotation.RequiresPermission;
import com.example.hbsz_idc_system_end.common.result.Result;
import com.example.hbsz_idc_system_end.dto.PageResult;
import com.example.hbsz_idc_system_end.dto.PositionQueryDTO;
import com.example.hbsz_idc_system_end.dto.PositionSaveDTO;
import com.example.hbsz_idc_system_end.dto.PositionVO;
import com.example.hbsz_idc_system_end.service.IPositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位管理控制器。
 * <p>
 * 业务模块：岗位职级体系（系统管理子模块）。
 * 基础请求路径：{@code /system/positions}。
 * 提供岗位的增删改查及启用岗位列表查询，服务于后台岗位维护与下拉选择。
 *
 * @author hbsz
 */
@Tag(name = "岗位管理", description = "岗位职级定义、维护")
@RestController
@RequestMapping("/system/positions")
public class PositionController {

    private final IPositionService positionService;

    public PositionController(IPositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * 分页查询岗位列表。
     * <p>HTTP：GET /system/positions。
     * 权限：{@code position:query}。
     *
     * @param query 查询条件（编码/名称/状态 + 分页）
     * @return 岗位分页结果
     */
    @Operation(summary = "分页查询岗位列表")
    @RequiresPermission("position:query")
    @GetMapping
    public Result<PageResult<PositionVO>> queryPage(PositionQueryDTO query) {
        return Result.success(positionService.queryPage(query));
    }

    /**
     * 查询所有启用岗位（下拉选择用）。
     * <p>HTTP：GET /system/positions/all。
     * 权限：{@code position:query}。
     *
     * @return 启用岗位列表
     */
    @Operation(summary = "查询所有启用岗位（下拉选择用）")
    @RequiresPermission("position:query")
    @GetMapping("/all")
    public Result<List<PositionVO>> listAll() {
        return Result.success(positionService.listAll());
    }

    /**
     * 查询岗位详情。
     * <p>HTTP：GET /system/positions/{id}。
     * 权限：{@code position:query}。
     *
     * @param id 岗位ID
     * @return 岗位详情
     */
    @Operation(summary = "查询岗位详情")
    @RequiresPermission("position:query")
    @GetMapping("/{id}")
    public Result<PositionVO> getById(@PathVariable Long id) {
        return Result.success(positionService.getDetail(id));
    }

    /**
     * 新增岗位。
     * <p>HTTP：POST /system/positions。
     * 权限：{@code position:add}。
     *
     * @param dto 岗位保存数据
     * @return 新增后的岗位
     */
    @Operation(summary = "新增岗位")
    @RequiresPermission("position:add")
    @PostMapping
    public Result<PositionVO> create(@Valid @RequestBody PositionSaveDTO dto) {
        return Result.success("新增岗位成功", positionService.create(dto));
    }

    /**
     * 修改岗位。
     * <p>HTTP：PUT /system/positions/{id}。
     * 权限：{@code position:edit}。
     *
     * @param id  岗位ID
     * @param dto 岗位保存数据
     * @return 修改后的岗位
     */
    @Operation(summary = "修改岗位")
    @RequiresPermission("position:edit")
    @PutMapping("/{id}")
    public Result<PositionVO> update(@PathVariable Long id, @Valid @RequestBody PositionSaveDTO dto) {
        return Result.success("修改岗位成功", positionService.update(id, dto));
    }

    /**
     * 删除岗位。
     * <p>HTTP：DELETE /system/positions/{id}。
     * 权限：{@code position:remove}。
     *
     * @param id 岗位ID
     * @return 无业务数据的操作结果
     */
    @Operation(summary = "删除岗位")
    @RequiresPermission("position:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.success("删除岗位成功", null);
    }

}
