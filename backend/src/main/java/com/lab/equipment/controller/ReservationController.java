package com.lab.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.dto.ApprovalDTO;
import com.lab.equipment.dto.ReservationBatchDTO;
import com.lab.equipment.dto.ReservationCreateDTO;
import com.lab.equipment.dto.ReservationModifyDTO;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.entity.User;
import com.lab.equipment.entity.Waitlist;
import com.lab.equipment.mapper.UserMapper;
import com.lab.equipment.service.ReservationService;
import com.lab.equipment.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 预约：创建/修改/查询/取消/领用确认/归还/候补；审批接口见 AdminReservationController */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "webp");
    private static final long MAX_PHOTO_SIZE = 10 * 1024 * 1024; // 10MB

    private final ReservationService reservationService;
    private final WaitlistService waitlistService;
    private final UserMapper userMapper;
    private final com.lab.equipment.service.GroupService groupService;
    private final com.lab.equipment.service.CancelBanService cancelBanService;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping
    public Result<Reservation> create(@Valid @RequestBody ReservationCreateDTO dto) {
        return Result.ok(reservationService.create(dto));
    }

    /** 教师/管理员：批量为本班学生提交预约 */
    @PostMapping("/batch")
    public Result<List<String>> batch(@Valid @RequestBody ReservationBatchDTO dto) {
        User operator = UserContext.get();
        if (!"TEACHER".equals(operator.getRole()) && !"ADMIN".equals(operator.getRole())) {
            throw new BusinessException("仅教师或管理员可批量代提交预约");
        }
        List<String> results = new ArrayList<>();
        for (String username : dto.getUsernames()) {
            User student = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, username.trim()));
            if (student == null) {
                results.add(username + "：账号不存在，已跳过");
                continue;
            }
            ReservationCreateDTO single = new ReservationCreateDTO();
            single.setEquipmentId(dto.getEquipmentId());
            single.setStartTime(dto.getStartTime());
            single.setEndTime(dto.getEndTime());
            single.setPurpose(dto.getPurpose() + "（教师 " + operator.getUsername() + " 代提交）");
            single.setSafetyAcknowledged(true); // 教师代提交视为已履行告知义务
            try {
                // 以该学生身份创建（复用全部校验规则；代提交不重复校验手册阅读）
                Reservation created = createFor(student, single);
                results.add(username + "：提交成功（预约 #" + created.getId() + "）");
            } catch (Exception e) {
                results.add(username + "：失败（" + e.getMessage() + "）");
            }
        }
        return Result.ok(results);
    }

    /** 以指定学生身份执行创建（临时切换 UserContext） */
    private Reservation createFor(User student, ReservationCreateDTO dto) {
        com.lab.equipment.common.UserContext.set(student);
        try {
            return reservationService.createOnBehalf(dto);
        } finally {
            com.lab.equipment.common.UserContext.clear();
        }
    }

    /** 修改预约时段（仅待审批状态） */
    @PutMapping("/modify")
    public Result<Void> modify(@Valid @RequestBody ReservationModifyDTO dto) {
        reservationService.modify(dto.getId(), UserContext.getUserId(), dto.getStartTime(), dto.getEndTime());
        return Result.ok();
    }

    /** 我的预约 */
    @GetMapping("/my")
    public Result<Page<Reservation>> my(@RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return Result.ok(reservationService.myPage(UserContext.getUserId(), status, page, size));
    }

    /** 当前用户的同时预约额度（上限 / 已占用 / 剩余 / 是否已达上限），供预约页置灰与提示 */
    @GetMapping("/quota")
    public Result<ReservationService.QuotaInfo> quota() {
        return Result.ok(reservationService.quota(UserContext.getUserId()));
    }

    /** 预约日历数据（month 格式 yyyy-MM）；管理员/保管员/安全员全部；学生=本人+同课题组成员；教师=本人+名下各课题组成员 */
    @GetMapping("/calendar")
    public Result<List<Reservation>> calendar(@RequestParam String month) {
        User user = UserContext.get();
        return Result.ok(reservationService.calendar(month,
                groupService.visibleReservationUserIds(user)));
    }

    /** 用户取消自己的预约；返回本次取消计入 30 天统计的次数（0 = 距开始 24 小时以上不计入） */
    @PutMapping("/{id}/cancel")
    public Result<java.util.Map<String, Object>> cancel(@PathVariable Long id) {
        int weight = reservationService.cancel(id, UserContext.getUserId());
        return Result.ok(java.util.Map.of("weight", weight));
    }

    /** 我的取消统计（近 30 天取消计次 / 阈值 / 是否封禁 / 剩余解禁天数） */
    @GetMapping("/cancel-stats")
    public Result<com.lab.equipment.service.CancelBanService.Stats> cancelStats() {
        return Result.ok(cancelBanService.stats(UserContext.getUserId()));
    }

    /** 领用确认（预约开始后的窗口内，领用照片必填，防止绕过前端直接提交） */
    @PutMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id,
                              @RequestParam(required = false) MultipartFile photo) throws Exception {
        if (photo == null || photo.isEmpty()) {
            throw new BusinessException("请上传仪器领用前实拍照片");
        }
        String photoPath = savePhoto(id, photo, "claim");
        reservationService.claim(id, UserContext.getUserId(), photoPath);
        return Result.ok();
    }

    /** 归还设备（本人或管理员/保管员，归还照片必填；管理员/保管员「标记归还」核验操作豁免） */
    @PutMapping("/{id}/return")
    public Result<Void> returnDevice(@PathVariable Long id,
                                     @RequestParam(required = false) MultipartFile photo) throws Exception {
        String role = UserContext.get().getRole();
        boolean manager = "ADMIN".equals(role) || "KEEPER".equals(role);
        if ((photo == null || photo.isEmpty()) && !manager) {
            throw new BusinessException("请上传仪器归还核验实拍照片");
        }
        String photoPath = savePhoto(id, photo, "return");
        reservationService.returnDevice(id, UserContext.get(), photoPath);
        return Result.ok();
    }

    /** 查看领用/归还照片（本人、管理员、保管员、安全员） */
    @GetMapping("/{id}/photo")
    public ResponseEntity<FileSystemResource> photo(@PathVariable Long id, @RequestParam String type) {
        Reservation reservation = reservationService.getById(id);
        if (reservation == null) throw new BusinessException("预约记录不存在");
        User user = UserContext.get();
        boolean own = reservation.getUserId().equals(user.getId());
        boolean viewer = "ADMIN".equals(user.getRole()) || "KEEPER".equals(user.getRole())
                || "SAFETY".equals(user.getRole());
        if (!own && !viewer) throw new BusinessException("无权限查看该照片");
        String path = "claim".equals(type) ? reservation.getClaimPhoto() : reservation.getReturnPhoto();
        if (!StringUtils.hasText(path)) {
            throw new BusinessException("该预约暂无" + ("claim".equals(type) ? "领用" : "归还") + "照片");
        }
        Path file = Paths.get(path);
        if (!Files.exists(file)) throw new BusinessException("照片文件不存在");
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        MediaType mediaType = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(file));
    }

    /* ---------------- 候补队列 ---------------- */

    /** 加入候补队列 */
    @PostMapping("/waitlist")
    public Result<Waitlist> joinWaitlist(@RequestBody ReservationCreateDTO dto) {
        return Result.ok(waitlistService.join(dto.getEquipmentId(), UserContext.getUserId(),
                dto.getStartTime(), dto.getEndTime()));
    }

    /** 我的候补记录 */
    @GetMapping("/waitlist/my")
    public Result<Page<Waitlist>> myWaitlist(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(waitlistService.myPage(UserContext.getUserId(), page, size));
    }

    /** 取消候补 */
    @PutMapping("/waitlist/{id}/cancel")
    public Result<Void> cancelWaitlist(@PathVariable Long id) {
        waitlistService.cancel(id, UserContext.getUserId());
        return Result.ok();
    }

    /* ---------------- 照片存储 ---------------- */

    /** 保存领用/归还照片，返回绝对路径；photo 为空时返回 null */
    private String savePhoto(Long id, MultipartFile photo, String type) throws Exception {
        if (photo == null || photo.isEmpty()) return null;
        if (photo.getSize() > MAX_PHOTO_SIZE) {
            throw new BusinessException("照片不能超过 10MB");
        }
        String original = photo.getOriginalFilename() == null ? "" : photo.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "jpg";
        if (!IMAGE_EXT.contains(ext)) {
            throw new BusinessException("仅支持 png/jpg/jpeg/webp 格式照片");
        }
        Path dir = Paths.get(uploadDir, "reservation", String.valueOf(id));
        Files.createDirectories(dir);
        Path target = dir.resolve(type + "-" + UUID.randomUUID() + "." + ext);
        photo.transferTo(target.toAbsolutePath());
        return target.toAbsolutePath().toString();
    }
}
