package com.lab.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.entity.EquipmentFile;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.EquipmentFileMapper;
import com.lab.equipment.mapper.EquipmentMapper;
import com.lab.equipment.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 设备资料库：附件按设备隔离。
 * 上传/删除仅管理员；列表/下载/预览所有登录用户。
 */
@RestController
@RequestMapping("/api/equipment-files")
@RequiredArgsConstructor
public class EquipmentFileController {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
            "png", "jpg", "jpeg", "gif", "webp");
    private static final long MAX_SIZE = 20 * 1024 * 1024; // 20MB

    private final EquipmentFileMapper equipmentFileMapper;
    private final EquipmentMapper equipmentMapper;
    private final UserMapper userMapper;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /** 某设备的附件列表（每台设备独立，天然隔离） */
    @GetMapping
    public Result<List<EquipmentFile>> list(@RequestParam Long equipmentId) {
        List<EquipmentFile> files = equipmentFileMapper.selectList(
                new LambdaQueryWrapper<EquipmentFile>()
                        .eq(EquipmentFile::getEquipmentId, equipmentId)
                        .orderByDesc(EquipmentFile::getCreateTime));
        // 批量补充上传人姓名
        Map<Long, String> names = new HashMap<>();
        for (EquipmentFile f : files) {
            if (f.getUploaderId() != null && !names.containsKey(f.getUploaderId())) {
                User u = userMapper.selectById(f.getUploaderId());
                if (u != null) {
                    names.put(u.getId(), StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
                }
            }
        }
        files.forEach(f -> f.setUploaderName(names.get(f.getUploaderId())));
        return Result.ok(files);
    }

    /** 管理员上传附件 */
    @PostMapping
    public Result<EquipmentFile> upload(@RequestParam Long equipmentId,
                                        @RequestParam("file") MultipartFile file) {
        requireAdmin();
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) throw new BusinessException("设备不存在");
        if (file == null || file.isEmpty()) throw new BusinessException("请选择文件");
        if (file.getSize() > MAX_SIZE) throw new BusinessException("文件不能超过 20MB");

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型，仅允许 PDF / Office / 图片 / TXT");
        }

        try {
            Path dir = Paths.get(uploadDir, "equipment", String.valueOf(equipmentId));
            Files.createDirectories(dir);
            String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(stored);
            file.transferTo(target.toAbsolutePath());

            EquipmentFile ef = new EquipmentFile();
            ef.setEquipmentId(equipmentId);
            ef.setFileName(original);
            ef.setFilePath(target.toAbsolutePath().toString());
            ef.setFileSize(file.getSize());
            ef.setFileType(ext);
            ef.setUploaderId(UserContext.getUserId());
            equipmentFileMapper.insert(ef);
            return Result.ok(ef);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败：" + e.getMessage());
        }
    }

    /** 管理员删除附件 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        EquipmentFile ef = equipmentFileMapper.selectById(id);
        if (ef == null) throw new BusinessException("附件不存在");
        try {
            Files.deleteIfExists(Paths.get(ef.getFilePath()));
        } catch (IOException ignored) { }
        equipmentFileMapper.deleteById(id);
        return Result.ok();
    }

    /** 下载 / 预览（预览传 inline=true） */
    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "false") boolean inline) {
        EquipmentFile ef = equipmentFileMapper.selectById(id);
        if (ef == null) throw new BusinessException("附件不存在");
        FileSystemResource resource = new FileSystemResource(ef.getFilePath());
        if (!resource.exists()) throw new BusinessException("文件已丢失，请联系管理员");

        String encoded = URLEncoder.encode(ef.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded;
        MediaType mediaType = switch (ef.getFileType() == null ? "" : ef.getFileType()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    private void requireAdmin() {
        User user = UserContext.get();
        if (!"ADMIN".equals(user.getRole())) {
            throw new BusinessException("无权限：该操作仅管理员可用");
        }
    }
}
