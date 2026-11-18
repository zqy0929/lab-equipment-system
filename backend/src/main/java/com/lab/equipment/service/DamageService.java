package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.dto.DamageHandleDTO;
import com.lab.equipment.dto.DamageReportDTO;
import com.lab.equipment.entity.DamageRecord;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.DamageRecordMapper;
import com.lab.equipment.mapper.EquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DamageService {

    private final DamageRecordMapper damageRecordMapper;
    private final EquipmentMapper equipmentMapper;
    private final GroupScopeService groupScopeService;
    private final MessageService messageService;

    /** 上报损耗；严重损耗自动将设备置为维修中 */
    @Transactional
    public void report(DamageReportDTO dto) {
        Equipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) throw new BusinessException("设备不存在");
        User user = UserContext.get();
        DamageRecord record = new DamageRecord();
        record.setEquipmentId(dto.getEquipmentId());
        record.setReporterId(user.getId());
        record.setReporterName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        record.setDescription(dto.getDescription());
        record.setSeverity(StringUtils.hasText(dto.getSeverity()) ? dto.getSeverity() : "MINOR");
        record.setStatus(DamageRecord.REPORTED);
        damageRecordMapper.insert(record);

        if ("SEVERE".equals(record.getSeverity())
                && Equipment.AVAILABLE.equals(equipment.getStatus())) {
            equipment.setStatus(Equipment.UNDER_REPAIR);
            equipmentMapper.updateById(equipment);
        }
        // 通知管理员与设备保管员：新报修待处理
        String equipName = equipment.getName();
        notifyRole("ADMIN", "新损耗上报待处理",
                "「" + equipName + "」被上报损耗（" + record.getReporterName() + "）：" + record.getDescription(), "DAMAGE");
        notifyRole("KEEPER", "新损耗上报待处理",
                "「" + equipName + "」被上报损耗（" + record.getReporterName() + "）：" + record.getDescription(), "DAMAGE");
    }

    /** 损耗分页（角色过滤）：学生/教师仅本人上报；管理员/保管员/安全员全部（安全核查用） */
    public Page<DamageRecord> page(String status, long page, long size) {
        User user = UserContext.get();
        QueryWrapper<DamageRecord> wrapper = new QueryWrapper<>();
        GroupScopeService.Scope scope = groupScopeService.scopeOf(user);
        if (scope == GroupScopeService.Scope.SELF) {
            wrapper.eq("d.reporter_id", user.getId());
        }
        if (StringUtils.hasText(status)) wrapper.eq("d.status", status);
        wrapper.orderByDesc("d.create_time");
        return damageRecordMapper.selectWithDetailPage(new Page<>(page, size), wrapper);
    }

    /** 管理员/保管员处理损耗记录；状态变化实时推送上报人（报修处理进度通知） */
    @Transactional
    public void handle(DamageHandleDTO dto) {
        DamageRecord record = damageRecordMapper.selectById(dto.getId());
        if (record == null) throw new BusinessException("损耗记录不存在");
        if (DamageRecord.FIXED.equals(record.getStatus())) {
            throw new BusinessException("该记录已处理完成");
        }
        if (!DamageRecord.FIXING.equals(dto.getStatus()) && !DamageRecord.FIXED.equals(dto.getStatus())) {
            throw new BusinessException("处理状态只能是 FIXING 或 FIXED");
        }
        User admin = UserContext.get();
        boolean toFixed = DamageRecord.FIXED.equals(dto.getStatus());
        record.setStatus(dto.getStatus());
        record.setCost(dto.getCost());
        record.setHandleNote(dto.getHandleNote());
        record.setHandlerName(StringUtils.hasText(admin.getRealName()) ? admin.getRealName() : admin.getUsername());
        record.setHandleTime(LocalDateTime.now());
        damageRecordMapper.updateById(record);

        // 修复完成且设备仍处于维修中 → 恢复为可预约
        if (toFixed) {
            Equipment equipment = equipmentMapper.selectById(record.getEquipmentId());
            if (equipment != null && Equipment.UNDER_REPAIR.equals(equipment.getStatus())) {
                equipment.setStatus(Equipment.AVAILABLE);
                equipmentMapper.updateById(equipment);
            }
        }
        // 报修处理进度通知（通知上报人）
        if (record.getReporterId() != null) {
            String equipName = equipmentNameOf(record.getEquipmentId());
            if (toFixed) {
                messageService.push(record.getReporterId(), "报修已完成",
                        "您上报的「" + equipName + "」损耗已修复完成。处理人：" + record.getHandlerName()
                                + (StringUtils.hasText(record.getHandleNote()) ? "，说明：" + record.getHandleNote() : ""),
                        "DAMAGE");
            } else {
                messageService.push(record.getReporterId(), "报修处理中",
                        "您上报的「" + equipName + "」损耗已进入维修流程。处理人：" + record.getHandlerName()
                                + (StringUtils.hasText(record.getHandleNote()) ? "，说明：" + record.getHandleNote() : ""),
                        "DAMAGE");
            }
        }
    }

    private String equipmentNameOf(Long equipmentId) {
        Equipment e = equipmentMapper.selectById(equipmentId);
        return e != null ? e.getName() : "设备";
    }

    /** 向某角色的全部启用用户推送站内消息 */
    private void notifyRole(String role, String title, String content, String type) {
        for (User u : groupScopeService.usersByRole(role)) {
            messageService.push(u.getId(), title, content, type);
        }
    }
}
