package com.lab.equipment.controller;

import com.lab.equipment.common.Result;
import com.lab.equipment.common.UserContext;
import com.lab.equipment.service.ExportService;
import com.lab.equipment.service.GroupService;
import com.lab.equipment.service.GroupScopeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 报表导出：预约记录 / 设备台账 / 损耗维修记录。
 * 预约导出：管理员/保管员全部；学生/教师按课题组共享域（本人+组员）；
 * 损耗导出：学生/教师仅本人（课题组不共享损耗）；管理员/保管员/安全员全部。
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final GroupScopeService groupScopeService;
    private final GroupService groupService;

    /** 预约记录表（按角色课题组共享域收敛） */
    @GetMapping("/reservations")
    public void reservations(@RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "true") boolean mine,
                             HttpServletResponse response) throws IOException {
        com.lab.equipment.entity.User user = com.lab.equipment.common.UserContext.get();
        List<Long> visibleIds = groupService.visibleReservationUserIds(user);
        if (visibleIds != null) {
            write(response, "预约记录表", exportService.exportReservationsByUsers(status, visibleIds));
            return;
        }
        Long userId = mine ? UserContext.getUserId() : null;
        write(response, "预约记录表", exportService.exportReservations(status, userId));
    }

    /** 设备台账表 */
    @GetMapping("/equipment")
    public void equipment(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String tag,
                          HttpServletResponse response) throws IOException {
        List<String> tags = tag == null ? List.of()
                : java.util.Arrays.stream(tag.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        write(response, "设备台账表", exportService.exportEquipment(keyword, category, status, tags));
    }

    /** 损耗维修记录表（学生/教师仅本人；管理员/保管员/安全员全部） */
    @GetMapping("/damages")
    public void damages(@RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "false") boolean mine,
                        HttpServletResponse response) throws IOException {
        com.lab.equipment.entity.User user = com.lab.equipment.common.UserContext.get();
        List<Long> reporterIds = null;
        if (groupScopeService.scopeOf(user) == GroupScopeService.Scope.SELF) {
            reporterIds = List.of(user.getId());
        } else if (mine) {
            reporterIds = List.of(user.getId());
        }
        write(response, "损耗维修记录表", exportService.exportDamages(status, reporterIds));
    }

    private void write(HttpServletResponse response, String name, byte[] bytes) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encoded = URLEncoder.encode(name + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.getOutputStream().write(bytes);
    }
}
