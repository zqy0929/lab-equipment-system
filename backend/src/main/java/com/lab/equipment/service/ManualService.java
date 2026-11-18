package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.entity.EquipmentManual;
import com.lab.equipment.entity.ManualReadRecord;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.EquipmentManualMapper;
import com.lab.equipment.mapper.EquipmentMapper;
import com.lab.equipment.mapper.ManualReadRecordMapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

/** 设备手册：全员可读，编辑/上传仅管理员与设备保管员 */
@Service
@RequiredArgsConstructor
public class ManualService {

    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx", "txt", "png", "jpg", "jpeg", "webp");
    private static final long MAX_SIZE = 20 * 1024 * 1024; // 20MB
    /** 历史测试数据，不在手册列表中展示 */
    private static final Set<String> HIDDEN_EQUIPMENT_NAMES = Set.of("测试保管员设备");

    private final EquipmentManualMapper manualMapper;
    private final EquipmentMapper equipmentMapper;
    private final ManualReadRecordMapper readRecordMapper;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /** 全部手册（含尚未创建手册的设备，供前端一次性渲染设备列表）
     *  - 学生：仅返回“开放预约”（状态为可预约）的设备；
     *  - 管理员 / 保管员：返回全部设备（含维修中、已报废），便于补录手册。 */
    public List<Map<String, Object>> listAll(String keyword, String category) {
        LambdaQueryWrapper<Equipment> query = new LambdaQueryWrapper<Equipment>()
                .like(StringUtils.isNotBlank(keyword), Equipment::getName, keyword)
                .eq(StringUtils.isNotBlank(category), Equipment::getCategory, category)
                .notIn(!HIDDEN_EQUIPMENT_NAMES.isEmpty(), Equipment::getName, HIDDEN_EQUIPMENT_NAMES)
                .orderByAsc(Equipment::getCategory)
                .orderByAsc(Equipment::getName);
        User viewer = UserContext.get();
        boolean student = viewer != null && "STUDENT".equals(viewer.getRole());
        if (student) {
            query.eq(Equipment::getStatus, Equipment.AVAILABLE);
        }
        List<Equipment> equipment = equipmentMapper.selectList(query);
        Map<Long, EquipmentManual> manualMap = new HashMap<>();
        for (EquipmentManual m : manualMapper.selectList(null)) {
            manualMap.put(m.getEquipmentId(), m);
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Equipment e : equipment) {
            Map<String, Object> row = new HashMap<>();
            row.put("equipmentId", e.getId());
            row.put("name", e.getName());
            row.put("model", e.getModel());
            row.put("category", e.getCategory());
            row.put("location", e.getLocation());
            row.put("status", e.getStatus());
            row.put("openForReserve", Equipment.AVAILABLE.equals(e.getStatus()));
            EquipmentManual m = manualMap.get(e.getId());
            row.put("hasManual", m != null && (StringUtils.isNotBlank(m.getContent())
                    || StringUtils.isNotBlank(m.getSafetyNotes()) || StringUtils.isNotBlank(m.getAttachmentName())));
            row.put("updateTime", m != null ? m.getUpdateTime() : null);
            row.put("editorName", m != null ? m.getEditorName() : null);
            result.add(row);
        }
        return result;
    }

    /** 某设备手册详情（无手册时返回仅含设备信息的空结构） */
    public Map<String, Object> detail(Long equipmentId) {
        Equipment e = equipmentMapper.selectById(equipmentId);
        if (e == null || HIDDEN_EQUIPMENT_NAMES.contains(e.getName())) throw new BusinessException("设备不存在");
        User viewer = UserContext.get();
        boolean student = viewer != null && "STUDENT".equals(viewer.getRole());
        // 学生只能查看开放预约的设备手册
        if (student && !Equipment.AVAILABLE.equals(e.getStatus())) {
            throw new BusinessException("该设备当前未开放预约，暂不可查看");
        }
        EquipmentManual m = manualMapper.selectOne(new LambdaQueryWrapper<EquipmentManual>()
                .eq(EquipmentManual::getEquipmentId, equipmentId));
        Map<String, Object> data = new HashMap<>();
        data.put("equipmentId", e.getId());
        data.put("name", e.getName());
        data.put("model", e.getModel());
        data.put("category", e.getCategory());
        data.put("location", e.getLocation());
        data.put("status", e.getStatus());
        data.put("openForReserve", Equipment.AVAILABLE.equals(e.getStatus()));
        data.put("description", e.getDescription());
        data.put("content", m != null ? m.getContent() : null);
        data.put("safetyNotes", m != null ? m.getSafetyNotes() : null);
        data.put("attachmentName", m != null ? m.getAttachmentName() : null);
        data.put("attachmentType", m != null ? m.getAttachmentType() : null);
        data.put("editorName", m != null ? m.getEditorName() : null);
        data.put("updateTime", m != null ? m.getUpdateTime() : null);
        // 当前用户的阅读确认情况（学生预约前校验使用）
        ManualReadRecord record = viewer == null ? null : latestRead(viewer.getId(), equipmentId);
        data.put("hasRead", record != null);
        data.put("readTime", record != null ? record.getReadTime() : null);
        if (viewer != null && ("ADMIN".equals(viewer.getRole()) || "KEEPER".equals(viewer.getRole()))) {
            data.put("readCount", readCount(equipmentId));
        }
        return data;
    }

    /** 记录一次“已阅读并确认”行为（学生预约前触发；管理员 / 保管员查看时也可记录） */
    public Map<String, Object> confirmRead(Long equipmentId) {
        Equipment e = equipmentMapper.selectById(equipmentId);
        if (e == null) throw new BusinessException("设备不存在");
        User user = UserContext.get();
        ManualReadRecord record = new ManualReadRecord();
        record.setUserId(user.getId());
        record.setEquipmentId(equipmentId);
        record.setEquipmentName(e.getName());
        record.setReadTime(LocalDateTime.now());
        record.setIp(com.lab.equipment.service.OpLogService.currentIp());
        readRecordMapper.insert(record);
        Map<String, Object> data = new HashMap<>();
        data.put("hasRead", true);
        data.put("readTime", record.getReadTime());
        return data;
    }

    /** 是否已确认阅读该设备手册（以最新一次确认记录为准） */
    public boolean hasRead(Long userId, Long equipmentId) {
        return latestRead(userId, equipmentId) != null;
    }

    private ManualReadRecord latestRead(Long userId, Long equipmentId) {
        return readRecordMapper.selectOne(new LambdaQueryWrapper<ManualReadRecord>()
                .eq(ManualReadRecord::getUserId, userId)
                .eq(ManualReadRecord::getEquipmentId, equipmentId)
                .orderByDesc(ManualReadRecord::getReadTime)
                .last("LIMIT 1"));
    }

    /** 该设备手册是否有可阅读的内容（无内容则不强制阅读确认） */
    public boolean hasReadableContent(Long equipmentId) {
        EquipmentManual m = manualMapper.selectOne(new LambdaQueryWrapper<EquipmentManual>()
                .eq(EquipmentManual::getEquipmentId, equipmentId));
        return m != null && (StringUtils.isNotBlank(m.getContent()) || StringUtils.isNotBlank(m.getSafetyNotes()));
    }

    /** 设备手册阅读确认人次（管理员 / 保管员查看统计用） */
    public long readCount(Long equipmentId) {
        return readRecordMapper.selectCount(new LambdaQueryWrapper<ManualReadRecord>()
                .eq(ManualReadRecord::getEquipmentId, equipmentId));
    }

    /** 保存手册文本内容（管理员/保管员） */
    public void save(Long equipmentId, String content, String safetyNotes) {
        requireManager();
        Equipment e = equipmentMapper.selectById(equipmentId);
        if (e == null) throw new BusinessException("设备不存在");
        User editor = UserContext.get();
        EquipmentManual m = manualMapper.selectOne(new LambdaQueryWrapper<EquipmentManual>()
                .eq(EquipmentManual::getEquipmentId, equipmentId));
        if (m == null) {
            m = new EquipmentManual();
            m.setEquipmentId(equipmentId);
        }
        m.setContent(content);
        m.setSafetyNotes(safetyNotes);
        m.setEditorId(editor.getId());
        m.setEditorName(StringUtils.isNotBlank(editor.getRealName()) ? editor.getRealName() : editor.getUsername());
        m.setUpdateTime(LocalDateTime.now());
        if (m.getId() == null) manualMapper.insert(m);
        else manualMapper.updateById(m);
    }

    /** 上传/替换手册附件（管理员/保管员） */
    public void uploadAttachment(Long equipmentId, MultipartFile file) {
        requireManager();
        Equipment e = equipmentMapper.selectById(equipmentId);
        if (e == null) throw new BusinessException("设备不存在");
        if (file == null || file.isEmpty()) throw new BusinessException("请选择文件");
        if (file.getSize() > MAX_SIZE) throw new BusinessException("附件不能超过 20MB");
        String original = file.getOriginalFilename() == null ? "manual" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的附件类型，仅允许 PDF / Word / 图片 / TXT");
        }
        try {
            Path dir = Paths.get(uploadDir, "manual", String.valueOf(equipmentId));
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID().toString().replace("-", "") + "." + ext);
            file.transferTo(target.toAbsolutePath());

            EquipmentManual m = manualMapper.selectOne(new LambdaQueryWrapper<EquipmentManual>()
                    .eq(EquipmentManual::getEquipmentId, equipmentId));
            if (m == null) {
                m = new EquipmentManual();
                m.setEquipmentId(equipmentId);
            }
            // 替换附件时删除旧文件
            if (StringUtils.isNotBlank(m.getAttachmentPath())) {
                try { Files.deleteIfExists(Paths.get(m.getAttachmentPath())); } catch (Exception ignored) { }
            }
            User editor = UserContext.get();
            m.setAttachmentName(original);
            m.setAttachmentPath(target.toAbsolutePath().toString());
            m.setAttachmentType(ext);
            m.setEditorId(editor.getId());
            m.setEditorName(StringUtils.isNotBlank(editor.getRealName()) ? editor.getRealName() : editor.getUsername());
            m.setUpdateTime(LocalDateTime.now());
            if (m.getId() == null) manualMapper.insert(m);
            else manualMapper.updateById(m);
        } catch (Exception ex) {
            throw new BusinessException("附件保存失败：" + ex.getMessage());
        }
    }

    /** 删除手册附件（管理员/保管员） */
    public void deleteAttachment(Long equipmentId) {
        requireManager();
        EquipmentManual m = manualMapper.selectOne(new LambdaQueryWrapper<EquipmentManual>()
                .eq(EquipmentManual::getEquipmentId, equipmentId));
        if (m == null || StringUtils.isBlank(m.getAttachmentPath())) {
            throw new BusinessException("该设备暂无手册附件");
        }
        try { Files.deleteIfExists(Paths.get(m.getAttachmentPath())); } catch (Exception ignored) { }
        // updateById 会忽略 null 字段，必须用 UpdateWrapper 显式置空
        manualMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<EquipmentManual>()
                .eq(EquipmentManual::getId, m.getId())
                .set(EquipmentManual::getAttachmentName, null)
                .set(EquipmentManual::getAttachmentPath, null)
                .set(EquipmentManual::getAttachmentType, null));
    }

    /** 手册附件存储路径（供控制器读取文件） */
    public String attachmentPath(Long equipmentId) {
        EquipmentManual m = requireAttachment(equipmentId);
        return m.getAttachmentPath();
    }

    /** 手册附件原始文件名（供下载响应头使用） */
    public String attachmentName(Long equipmentId) {
        EquipmentManual m = requireAttachment(equipmentId);
        return StringUtils.isNotBlank(m.getAttachmentName()) ? m.getAttachmentName() : m.getAttachmentPath();
    }

    private EquipmentManual requireAttachment(Long equipmentId) {
        EquipmentManual m = manualMapper.selectOne(new LambdaQueryWrapper<EquipmentManual>()
                .eq(EquipmentManual::getEquipmentId, equipmentId));
        if (m == null || StringUtils.isBlank(m.getAttachmentPath())) {
            throw new BusinessException("该设备暂无手册附件");
        }
        return m;
    }

    /** 编辑权限：管理员 / 设备保管员 */
    private void requireManager() {
        User user = UserContext.get();
        if (!"ADMIN".equals(user.getRole()) && !"KEEPER".equals(user.getRole())) {
            throw new BusinessException("无权限：手册编辑仅管理员与设备保管员可用");
        }
    }
}
