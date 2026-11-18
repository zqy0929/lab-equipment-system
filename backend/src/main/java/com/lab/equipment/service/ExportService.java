package com.lab.equipment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.equipment.common.BusinessException;
import com.lab.equipment.entity.DamageRecord;
import com.lab.equipment.entity.Equipment;
import com.lab.equipment.entity.Reservation;
import com.lab.equipment.mapper.DamageRecordMapper;
import com.lab.equipment.mapper.EquipmentMapper;
import com.lab.equipment.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Excel 报表导出：预约记录 / 设备台账 / 损耗维修记录 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReservationMapper reservationMapper;
    private final EquipmentMapper equipmentMapper;
    private final DamageRecordMapper damageRecordMapper;

    /** 预约记录表（按用户域过滤：教师本组等场景） */
    public byte[] exportReservationsByUsers(String status, java.util.List<Long> userIds) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) wrapper.eq("r.status", status);
        if (userIds == null || userIds.isEmpty()) {
            wrapper.eq("r.user_id", -1L);
        } else {
            wrapper.in("r.user_id", userIds);
        }
        wrapper.orderByDesc("r.create_time");
        List<Reservation> list = reservationMapper.selectWithDetailList(wrapper);
        return toReservationWorkbook(list);
    }

    /** 预约记录表（支持状态过滤） */
    public byte[] exportReservations(String status, Long userId) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) wrapper.eq("r.status", status);
        if (userId != null) wrapper.eq("r.user_id", userId);
        wrapper.orderByDesc("r.create_time");
        List<Reservation> list = reservationMapper.selectWithDetailList(wrapper);
        return toReservationWorkbook(list);
    }

    private byte[] toReservationWorkbook(List<Reservation> list) {
        String[] headers = {"编号", "预约人", "设备名称", "型号", "设备标签", "开始时间", "结束时间", "用途/备注", "状态",
                "审批人", "驳回理由", "领用时间", "归还时间", "提交时间"};
        return buildWorkbook("预约记录", headers, list.size(), (row, i) -> {
            Reservation r = list.get(i);
            row.createCell(0).setCellValue(r.getId());
            row.createCell(1).setCellValue(nvl(r.getUsername()));
            row.createCell(2).setCellValue(nvl(r.getEquipmentName()));
            row.createCell(3).setCellValue(nvl(r.getEquipmentModel()));
            row.createCell(4).setCellValue(nvl(r.getEquipmentTags()));
            row.createCell(5).setCellValue(r.getStartTime() == null ? "" : r.getStartTime().format(FMT));
            row.createCell(6).setCellValue(r.getEndTime() == null ? "" : r.getEndTime().format(FMT));
            row.createCell(7).setCellValue(nvl(r.getPurpose()));
            row.createCell(8).setCellValue(statusLabel(r.getStatus()));
            row.createCell(9).setCellValue(nvl(r.getApproverName()));
            row.createCell(10).setCellValue(nvl(r.getRejectReason()));
            row.createCell(11).setCellValue(r.getClaimTime() == null ? "" : r.getClaimTime().format(FMT));
            row.createCell(12).setCellValue(r.getReturnTime() == null ? "" : r.getReturnTime().format(FMT));
            row.createCell(13).setCellValue(r.getCreateTime() == null ? "" : r.getCreateTime().format(FMT));
        });
    }

    /** 设备台账表（支持关键字/分类/状态过滤） */
    public byte[] exportEquipment(String keyword, String category, String status, java.util.List<String> tags) {
        QueryWrapper<Equipment> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("name", keyword).or().like("model", keyword));
        }
        if (StringUtils.hasText(category)) wrapper.eq("category", category);
        if (StringUtils.hasText(status)) wrapper.eq("status", status);
        if (tags != null && !tags.isEmpty()) {
            // 与名录页一致：多标签按 AND 叠加（设备大类 + 使用属性）
            tags.stream().filter(StringUtils::hasText).forEach(t -> wrapper.like("tags", t));
        }
        wrapper.orderByAsc("id");
        List<Equipment> list = equipmentMapper.selectList(wrapper);

        String[] headers = {"编号", "设备名称", "型号", "分类", "存放位置", "状态", "标签", "说明", "创建时间"};
        return buildWorkbook("设备台账", headers, list.size(), (row, i) -> {
            Equipment e = list.get(i);
            row.createCell(0).setCellValue(e.getId());
            row.createCell(1).setCellValue(nvl(e.getName()));
            row.createCell(2).setCellValue(nvl(e.getModel()));
            row.createCell(3).setCellValue(nvl(e.getCategory()));
            row.createCell(4).setCellValue(nvl(e.getLocation()));
            row.createCell(5).setCellValue(equipStatusLabel(e.getStatus()));
            row.createCell(6).setCellValue(nvl(e.getTags()));
            row.createCell(7).setCellValue(nvl(e.getDescription()));
            row.createCell(8).setCellValue(e.getCreateTime() == null ? "" : e.getCreateTime().format(FMT));
        });
    }

    /** 损耗维修记录表（支持状态过滤 + 上报人域过滤：学生仅本人、教师本组） */
    public byte[] exportDamages(String status, java.util.List<Long> reporterIds) {
        QueryWrapper<DamageRecord> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) wrapper.eq("d.status", status);
        if (reporterIds != null) {
            if (reporterIds.isEmpty()) {
                wrapper.eq("d.reporter_id", -1L); // 空域：不导出任何记录
            } else {
                wrapper.in("d.reporter_id", reporterIds);
            }
        }
        wrapper.orderByDesc("d.create_time");
        List<DamageRecord> list = damageRecordMapper.selectWithDetailList(wrapper);

        String[] headers = {"编号", "设备名称", "上报人", "故障描述", "严重程度", "状态", "维修费用(元)", "处理人", "处理备注", "上报时间", "处理时间"};
        return buildWorkbook("损耗维修记录", headers, list.size(), (row, i) -> {
            DamageRecord d = list.get(i);
            row.createCell(0).setCellValue(d.getId());
            row.createCell(1).setCellValue(nvl(d.getEquipmentName()));
            row.createCell(2).setCellValue(nvl(d.getReporterName()));
            row.createCell(3).setCellValue(nvl(d.getDescription()));
            row.createCell(4).setCellValue(severityLabel(d.getSeverity()));
            row.createCell(5).setCellValue(damageStatusLabel(d.getStatus()));
            row.createCell(6).setCellValue(d.getCost() == null ? "" : d.getCost().toPlainString());
            row.createCell(7).setCellValue(nvl(d.getHandlerName()));
            row.createCell(8).setCellValue(nvl(d.getHandleNote()));
            row.createCell(9).setCellValue(d.getCreateTime() == null ? "" : d.getCreateTime().format(FMT));
            row.createCell(10).setCellValue(d.getHandleTime() == null ? "" : d.getHandleTime().format(FMT));
        });
    }

    /* ---------------- 通用构建 ---------------- */

    private interface RowFiller {
        void fill(Row row, int index);
    }

    private byte[] buildWorkbook(String sheetName, String[] headers, int rows, RowFiller filler) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);

            CellStyle headStyle = wb.createCellStyle();
            Font headFont = wb.createFont();
            headFont.setBold(true);
            headFont.setColor(IndexedColors.WHITE.getIndex());
            headStyle.setFont(headFont);
            headStyle.setAlignment(HorizontalAlignment.CENTER);
            headStyle.setFillForegroundColor(IndexedColors.GREY_40_PERCENT.getIndex());
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);

            Row head = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                head.createCell(c).setCellValue(headers[c]);
                head.getCell(c).setCellStyle(headStyle);
                sheet.setColumnWidth(c, c == 0 ? 2400 : 5000);
            }

            for (int i = 0; i < rows; i++) {
                Row row = sheet.createRow(i + 1);
                filler.fill(row, i);
                for (int c = 0; c < headers.length; c++) {
                    row.getCell(c).setCellStyle(bodyStyle);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("导出失败：" + e.getMessage());
        }
    }

    private String nvl(String s) { return s == null ? "" : s; }

    private String statusLabel(String s) {
        return switch (s == null ? "" : s) {
            case Reservation.PENDING -> "待审批";
            case Reservation.APPROVED -> "借用中";
            case Reservation.CLAIMED -> "使用中";
            case Reservation.AUTO_RELEASED -> "自动释放";
            case Reservation.REJECTED -> "已驳回";
            case Reservation.RETURNED -> "已归还";
            case Reservation.CANCELLED -> "已取消";
            case Reservation.EXPIRED -> "已过期";
            default -> nvl(s);
        };
    }

    private String equipStatusLabel(String s) {
        return switch (s == null ? "" : s) {
            case Equipment.AVAILABLE -> "可用";
            case Equipment.UNDER_REPAIR -> "维修中";
            case Equipment.SCRAPPED -> "已报废";
            default -> nvl(s);
        };
    }

    private String damageStatusLabel(String s) {
        return switch (s == null ? "" : s) {
            case DamageRecord.REPORTED -> "待处理";
            case DamageRecord.FIXING -> "维修中";
            case DamageRecord.FIXED -> "已修复";
            default -> nvl(s);
        };
    }

    private String severityLabel(String s) {
        return switch (s == null ? "" : s) {
            case "MINOR" -> "轻微";
            case "MODERATE" -> "中等";
            case "SEVERE" -> "严重";
            default -> nvl(s);
        };
    }
}
