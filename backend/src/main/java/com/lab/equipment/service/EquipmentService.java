package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.mapper.EquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 设备业务服务类
 * 负责实验室设备的分页查询、新增、修改、删除、状态切换、分类查询等业务逻辑
 */
@Service
@RequiredArgsConstructor
public class EquipmentService {

    /**
     * 设备数据Mapper
     */
    private final EquipmentMapper equipmentMapper;
    /**
     * 操作日志服务
     */
    private final OpLogService opLogService;
    /**
     * 设备手册模板填充服务
     */
    private final ManualTemplateService manualTemplateService;

    /**
     * 分页查询设备列表，支持关键词、分类、状态、多标签筛选
     *
     * @param keyword  搜索关键词（匹配名称/型号/位置）
     * @param category 设备分类
     * @param status   设备状态
     * @param tags     标签列表
     * @param page     当前页码
     * @param size     每页条数
     * @return 分页设备数据
     */
    public Page<Equipment> page(final String keyword,
                                final String category,
                                final String status,
                                final List<String> tags,
                                final long page,
                                final long size) {
        LambdaQueryWrapper<Equipment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Equipment::getName, keyword)
                    .or().like(Equipment::getModel, keyword)
                    .or().like(Equipment::getLocation, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Equipment::getCategory, category);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Equipment::getStatus, status);
        }
        // 多标签按 AND 叠加：设备大类（光学仪器等）与使用属性（可外借等）需同时命中
        if (tags != null && !tags.isEmpty()) {
            tags.stream()
                    .filter(StringUtils::hasText)
                    .forEach(t -> wrapper.like(Equipment::getTags, t));
        }
        // 按录入顺序（id 升序）稳定排序，保证设备名录卡片顺序固定不跳动
        wrapper.orderByAsc(Equipment::getId);
        return equipmentMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 查询所有不重复的设备分类名称
     *
     * @return 分类名称集合
     */
    public List<String> categories() {
        return equipmentMapper.selectList(new LambdaQueryWrapper<Equipment>()
                        .select(Equipment::getCategory)
                        .groupBy(Equipment::getCategory))
                .stream()
                .map(Equipment::getCategory)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 供预约下拉框使用：仅返回可借用的设备
     *
     * @return 可预约设备列表
     */
    public List<Equipment> listAvailable() {
        return equipmentMapper.selectList(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getStatus, Equipment.AVAILABLE)
                .orderByAsc(Equipment::getName));
    }

    /**
     * 根据设备id查询设备详情
     *
     * @param id 设备主键id
     * @return 设备详情实体
     */
    public Equipment detail(final Long id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BusinessException("设备不存在");
        }
        return equipment;
    }

    /**
     * 新增设备信息
     *
     * @param equipment 待新增设备实体
     */
    public void add(final Equipment equipment) {
        equipment.setId(null);
        if (!StringUtils.hasText(equipment.getStatus())) {
            equipment.setStatus(Equipment.AVAILABLE);
        }
        equipmentMapper.insert(equipment);
        // 新设备自动带一份贴合用途的操作文档与安全注意事项（后续可由保管员/管理员修改）
        manualTemplateService.fillIfMissing(equipment);
        opLogService.log("CREATE_EQUIPMENT",
                "新增设备：「" + equipment.getName()
                        + "」（" + equipment.getModel() + "）");
    }

    /**
     * 更新设备信息
     *
     * @param equipment 设备实体对象，包含待更新的设备数据
     */
    public void update(final Equipment equipment) {
        Equipment before = requireEquipment(equipment.getId());
        equipmentMapper.updateById(equipment);
        opLogService.log("UPDATE_EQUIPMENT",
                "修改设备「" + before.getName() + "」：状态 "
                        + before.getStatus() + " → "
                        + equipment.getStatus()
                        + "，标签 " + before.getTags() + " → "
                        + equipment.getTags());
    }

    /**
     * 根据设备id删除设备记录
     *
     * @param id 设备主键id
     */
    public void delete(final Long id) {
        Equipment equipment = requireEquipment(id);
        equipmentMapper.deleteById(id);
        opLogService.log("DELETE_EQUIPMENT",
                "删除设备：「" + equipment.getName() + "」");
    }

    /**
     * 切换设备运行状态（正常 / 维修中 / 停用），供设备维护管理使用
     *
     * @param id     设备主键
     * @param status 目标状态
     */
    public void updateStatus(final Long id, final String status) {
        Equipment before = requireEquipment(id);
        if (status == null || !Set.of(Equipment.AVAILABLE,
                Equipment.UNDER_REPAIR,
                Equipment.SCRAPPED).contains(status)) {
            throw new BusinessException("无效的设备状态，仅支持 正常 / 维修中 / 停用");
        }
        if (status.equals(before.getStatus())) {
            return;
        }
        Equipment update = new Equipment();
        update.setId(id);
        update.setStatus(status);
        equipmentMapper.updateById(update);
        opLogService.log("UPDATE_EQUIPMENT_STATUS",
                "切换设备状态：「" + before.getName() + "」"
                        + before.getStatus() + " → " + status);
    }

    /**
     * 根据id获取设备，不存在则抛出异常
     *
     * @param id 设备主键
     * @return 设备实体
     */
    private Equipment requireEquipment(final Long id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BusinessException("设备不存在");
        }
        return equipment;
    }
}
