$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$file = "c:\Users\QianLong\Desktop\Hbsz\Hbsz_IDC_System_end\src\main\java\com\example\hbsz_idc_system_end\service\impl\TaskServiceImpl.java"
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)

# 5. Remove workload writeback block in reviewProgress
$workloadWriteback = @"
            // 工作量回写：审核通过后将本次填报的工时（workloadHours）累加到任务的实际已完成工作量。
            // 修复并发竞态：原实现为 read-modify-write（selectById → add → updateById），
            // 两个线程并发审核同一任务的不同进度日志时，后提交者覆盖前者的累加值（lost update）。
            // 改为：1) 将 actualWorkload 置 null，使 updateById 跳过该列（FieldStrategy=NOT_NULL）；
            //       2) 再用原子 UPDATE 累加 actualWorkload，利用 MySQL 行锁保证并发安全。
            BigDecimal incrementalWorkload = progressLog.getWorkloadHours();
            if (incrementalWorkload != null && incrementalWorkload.compareTo(BigDecimal.ZERO) > 0) {
                // 置 null 避免 updateById 覆盖并发线程的原子累加结果
                task.setActualWorkload(null);
            }
            int taskRows = taskMapper.updateById(task);
"@

$replacement = @"
            int taskRows = taskMapper.updateById(task);
"@

$beforeLen = $content.Length
$content = $content.Replace($workloadWriteback, $replacement)
$afterLen = $content.Length
Write-Output "5a: Before=$beforeLen, After=$afterLen, Diff=$($beforeLen - $afterLen)"

# Remove the addActualWorkload block after updateById logging
$addWorkloadBlock = @"
            // 原子累加实际工作量：UPDATE biz_task SET actual_workload = COALESCE(actual_workload,0) + #{incremental}
            // 利用 MySQL 行锁保证并发安全，两个线程并发执行时不会丢失累加值
            if (incrementalWorkload != null && incrementalWorkload.compareTo(BigDecimal.ZERO) > 0) {
                int workloadRows = taskMapper.addActualWorkload(task.getId(), incrementalWorkload);
                log.info("[进度审核][工作量累加] logId={}, taskId={}, incremental={}, affectedRows={}, reviewerId={}, requestTime={}",
                        logId, task.getId(), incrementalWorkload, workloadRows, reviewerId, requestTime);
            }
"@
$beforeLen = $content.Length
$content = $content.Replace($addWorkloadBlock, "")
$afterLen = $content.Length
Write-Output "5b: Before=$beforeLen, After=$afterLen, Diff=$($beforeLen - $afterLen)"

# 6. Remove workload fields from convertToVO
$convertToVOBlock = @"
        // 工作量字段透传：前端用于显示工作量基准、实际完成量与完成率
        vo.setPlannedWorkload(task.getPlannedWorkload());
        vo.setActualWorkload(task.getActualWorkload());
        vo.setWorkloadUnit(task.getWorkloadUnit());
"@
$content = $content.Replace($convertToVOBlock, "        // (工作量字段已移除)")

# 7. Remove workload fields from convertLogToVO
$content = $content -replace "        vo\.setWorkloadDescription\(log\.getWorkloadDescription\(\)\);\r?\n", ""
$content = $content -replace "        vo\.setWorkloadHours\(log\.getWorkloadHours\(\)\);\r?\n", ""

[System.IO.File]::WriteAllText($file, $content, $utf8NoBom)
Write-Output "All done. Content length: $($content.Length)"