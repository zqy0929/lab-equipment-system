package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.service.ManualService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** 设备手册：全员可读；编辑/上传/删除附件仅管理员与设备保管员（服务层二次校验） */
@RestController
@RequestMapping("/api/manuals")
@RequiredArgsConstructor
public class ManualController {

    private final ManualService manualService;

    /** 手册总览（全部设备 + 手册完成情况；学生仅返回开放预约的设备） */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category) {
        return Result.ok(manualService.listAll(keyword, category));
    }

    /** 某设备手册详情 */
    @GetMapping("/{equipmentId}")
    public Result<Map<String, Object>> detail(@PathVariable Long equipmentId) {
        return Result.ok(manualService.detail(equipmentId));
    }

    /** 确认已阅读该设备手册（学生预约前强制确认，写入阅读记录） */
    @PostMapping("/{equipmentId}/read")
    public Result<Map<String, Object>> confirmRead(@PathVariable Long equipmentId) {
        return Result.ok(manualService.confirmRead(equipmentId));
    }

    /** 保存手册文本（管理员/保管员） */
    @PutMapping("/{equipmentId}")
    public Result<Void> save(@PathVariable Long equipmentId,
                             @RequestBody Map<String, String> body) {
        manualService.save(equipmentId, body.get("content"), body.get("safetyNotes"));
        return Result.ok();
    }

    /** 上传/替换手册附件（管理员/保管员） */
    @PostMapping("/{equipmentId}/attachment")
    public Result<Void> upload(@PathVariable Long equipmentId,
                               @RequestParam("file") MultipartFile file) throws Exception {
        manualService.uploadAttachment(equipmentId, file);
        return Result.ok();
    }

    /** 删除手册附件（管理员/保管员） */
    @DeleteMapping("/{equipmentId}/attachment")
    public Result<Void> deleteAttachment(@PathVariable Long equipmentId) {
        manualService.deleteAttachment(equipmentId);
        return Result.ok();
    }

    /** 下载 / 预览手册附件 */
    @GetMapping("/{equipmentId}/attachment")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long equipmentId,
                                                       @RequestParam(defaultValue = "false") boolean inline) {
        Path file = Paths.get(manualService.attachmentPath(equipmentId));
        if (!Files.exists(file)) throw new com.lab.equipment.common.BusinessException("手册附件文件不存在");
        String fileName = file.getFileName().toString();
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        MediaType mediaType = switch (ext) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        // 原始文件名从服务层取
        String original = manualService.attachmentName(equipmentId);
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new FileSystemResource(file));
    }
}
