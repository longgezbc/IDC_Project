package com.example.hbsz_idc_system_end.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hbsz_idc_system_end.entity.BizCustomer;
import com.example.hbsz_idc_system_end.entity.BizDocument;
import com.example.hbsz_idc_system_end.entity.BizProject;
import com.example.hbsz_idc_system_end.mapper.BizCustomerMapper;
import com.example.hbsz_idc_system_end.mapper.BizDocumentMapper;
import com.example.hbsz_idc_system_end.mapper.BizProjectMapper;
import com.example.hbsz_idc_system_end.utils.PinyinUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 名称拼音冗余列启动回量。
 * <p>
 * 背景：V20261104 迁移为 biz_project / biz_document / biz_customer 增加
 * name_pinyin_full / name_pinyin_initials 冗余列，汉字→拼音转换需 Java 侧
 * TinyPinyin 完成（SQL 无法实现），故存量数据在应用启动时回量。
 * <p>
 * 幂等策略：仅处理 name_pinyin_full IS NULL 的行，逐行计算后 updateById；
 * 新增/改名数据由 MetaObjectHandler 随写入自动填充，本 Runner 只兜底存量。
 * 每次启动执行一次空查询（无待回量行时零写入），开销可忽略。
 *
 * @author hbsz
 */
@Slf4j
@Component
public class PinyinBackfillRunner implements ApplicationRunner {

    private final BizProjectMapper projectMapper;
    private final BizDocumentMapper documentMapper;
    private final BizCustomerMapper customerMapper;

    public PinyinBackfillRunner(BizProjectMapper projectMapper,
                                BizDocumentMapper documentMapper,
                                BizCustomerMapper customerMapper) {
        this.projectMapper = projectMapper;
        this.documentMapper = documentMapper;
        this.customerMapper = customerMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 防御：回量失败（如表未迁移、测试环境 H2 无表）不阻断应用启动，下次启动自动重试
        int projects = safeBackfill("项目", this::backfillProjects);
        int documents = safeBackfill("文档", this::backfillDocuments);
        int customers = safeBackfill("客户", this::backfillCustomers);
        if (projects + documents + customers > 0) {
            log.info("[拼音回量] 完成: 项目={} 文档={} 客户={}", projects, documents, customers);
        }
    }

    private int safeBackfill(String label, java.util.function.Supplier<Integer> task) {
        try {
            return task.get();
        } catch (Exception e) {
            log.warn("[拼音回量] {}回量失败（不影响启动，下次启动重试）: {}", label, e.getMessage());
            return 0;
        }
    }

    private int backfillProjects() {
        List<BizProject> list = projectMapper.selectList(new LambdaQueryWrapper<BizProject>()
                .isNull(BizProject::getNamePinyinFull)
                .or()
                .isNull(BizProject::getNamePinyinInitials));
        int count = 0;
        for (BizProject p : list) {
            if (p.getName() == null || p.getName().isBlank()) {
                continue;
            }
            p.setNamePinyinFull(PinyinUtils.toFullPinyin(p.getName()));
            p.setNamePinyinInitials(PinyinUtils.toInitials(p.getName()));
            projectMapper.updateById(p);
            count++;
        }
        return count;
    }

    private int backfillDocuments() {
        List<BizDocument> list = documentMapper.selectList(new LambdaQueryWrapper<BizDocument>()
                .isNull(BizDocument::getNamePinyinFull)
                .or()
                .isNull(BizDocument::getNamePinyinInitials));
        int count = 0;
        for (BizDocument d : list) {
            if (d.getName() == null || d.getName().isBlank()) {
                continue;
            }
            d.setNamePinyinFull(PinyinUtils.toFullPinyin(d.getName()));
            d.setNamePinyinInitials(PinyinUtils.toInitials(d.getName()));
            documentMapper.updateById(d);
            count++;
        }
        return count;
    }

    private int backfillCustomers() {
        List<BizCustomer> list = customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                .isNull(BizCustomer::getNamePinyinFull)
                .or()
                .isNull(BizCustomer::getNamePinyinInitials));
        int count = 0;
        for (BizCustomer c : list) {
            if (c.getName() == null || c.getName().isBlank()) {
                continue;
            }
            c.setNamePinyinFull(PinyinUtils.toFullPinyin(c.getName()));
            c.setNamePinyinInitials(PinyinUtils.toInitials(c.getName()));
            customerMapper.updateById(c);
            count++;
        }
        return count;
    }
}
