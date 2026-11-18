package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.CategoryRule;
import com.lab.equipment.mapper.CategoryRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 设备分类预约时长规则：按设备分类分别控制单次预约的最短/最长时长 */
@Service
@RequiredArgsConstructor
public class CategoryRuleService {

    /** 系统预置的 7 类设备分类 */
    public static final List<String> CATEGORIES = List.of(
            "常规仪器", "光谱分析", "分子生物", "分离纯化", "显微成像", "清洗灭菌", "储存设备");

    private static final int DEFAULT_MIN = 30;
    private static final int DEFAULT_MAX = 240;

    private final CategoryRuleMapper categoryRuleMapper;
    private final SysConfigService sysConfigService;
    private final OpLogService opLogService;

    /** 全部分类规则（预置 7 类必定返回，库中额外分类追加在后） */
    public List<CategoryRule> list() {
        Map<String, CategoryRule> map = new LinkedHashMap<>();
        for (CategoryRule r : categoryRuleMapper.selectList(null)) {
            if (r.getCategory() != null) map.put(r.getCategory().trim(), r);
        }
        List<CategoryRule> out = new ArrayList<>();
        for (String c : CATEGORIES) {
            CategoryRule r = map.get(c);
            if (r == null) {
                r = new CategoryRule();
                r.setCategory(c);
                r.setMinDuration(DEFAULT_MIN);
                r.setMaxDuration(DEFAULT_MAX);
            }
            out.add(r);
        }
        for (CategoryRule r : map.values()) {
            if (!CATEGORIES.contains(r.getCategory())) out.add(r);
        }
        return out;
    }

    /** 某分类的时长规则；分类未配置时回落到全局配置兜底 */
    public CategoryRule ruleOf(String category) {
        CategoryRule rule = null;
        if (category != null && !category.isBlank()) {
            rule = categoryRuleMapper.selectOne(new LambdaQueryWrapper<CategoryRule>()
                    .eq(CategoryRule::getCategory, category.trim())
                    .last("LIMIT 1"));
        }
        if (rule != null && rule.getMinDuration() != null && rule.getMaxDuration() != null) {
            return rule;
        }
        CategoryRule fallback = new CategoryRule();
        fallback.setCategory(category == null ? "" : category);
        fallback.setMinDuration(sysConfigService.getInt(SysConfigService.KEY_MIN_DURATION));
        fallback.setMaxDuration(sysConfigService.getInt(SysConfigService.KEY_MAX_DURATION));
        return fallback;
    }

    /** 分类对应的最短时长（分钟） */
    public int minOf(String category) {
        return ruleOf(category).getMinDuration();
    }

    /** 分类对应的最长时长（分钟） */
    public int maxOf(String category) {
        return ruleOf(category).getMaxDuration();
    }

    /** 批量保存分类规则（存在则更新，不存在则插入），并写审计日志 */
    @Transactional
    public void updateBatch(List<CategoryRule> rules) {
        if (rules == null || rules.isEmpty()) throw new BusinessException("请提交需要保存的分类规则");
        StringBuilder detail = new StringBuilder();
        for (CategoryRule item : rules) {
            String category = item.getCategory() == null ? "" : item.getCategory().trim();
            if (category.isEmpty()) throw new BusinessException("设备分类名称不能为空");
            Integer min = item.getMinDuration();
            Integer max = item.getMaxDuration();
            if (min == null || max == null) throw new BusinessException("「" + category + "」的最短/最长时长不能为空");
            if (min < 5) throw new BusinessException("「" + category + "」最短时长不得少于 5 分钟");
            if (max > 1440) throw new BusinessException("「" + category + "」最长时长不得超过 1440 分钟（24 小时）");
            if (min > max) throw new BusinessException("「" + category + "」最短时长不能大于最长时长");

            CategoryRule exist = categoryRuleMapper.selectOne(new LambdaQueryWrapper<CategoryRule>()
                    .eq(CategoryRule::getCategory, category)
                    .last("LIMIT 1"));
            if (exist != null) {
                detail.append(category).append(" ").append(exist.getMinDuration()).append("-").append(exist.getMaxDuration())
                        .append(" → ").append(min).append("-").append(max).append(" 分钟；");
                exist.setMinDuration(min);
                exist.setMaxDuration(max);
                categoryRuleMapper.updateById(exist);
            } else {
                detail.append(category).append(" 新增 ").append(min).append("-").append(max).append(" 分钟；");
                CategoryRule r = new CategoryRule();
                r.setCategory(category);
                r.setMinDuration(min);
                r.setMaxDuration(max);
                categoryRuleMapper.insert(r);
            }
        }
        opLogService.log("UPDATE_CATEGORY_RULE", "修改设备分类预约时长：" + detail);
    }
}
