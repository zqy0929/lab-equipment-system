package com.lab.equipment.service;

import com.lab.equipment.common.BusinessException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：注册等匿名接口的人机校验。
 *
 * 设计要点：
 * - 验证码答案只存服务端内存（captchaId -> code），前端仅拿到扭曲图片，防止被脚本直接读出答案；
 * - 一次性使用：校验通过或校验失败后即销毁，防止重放；
 * - 5 分钟过期，定时清理防止内存泄漏。
 */
@Service
public class CaptchaService {

    /** 验证码字符集：剔除易混淆的 0/O/1/I/L */
    private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** 验证码位数与有效期 */
    private static final int CODE_LENGTH = 4;
    private static final int EXPIRE_MINUTES = 5;

    /** 图片尺寸 */
    private static final int WIDTH = 130;
    private static final int HEIGHT = 44;

    /** 干扰线数量 */
    private static final int NOISE_LINES = 10;

    /** 字符可选颜色（莫兰迪色系） */
    private static final Color[] CHAR_COLORS = {
            new Color(0x18, 0x5F, 0xA5), new Color(0x0F, 0x6E, 0x56),
            new Color(0x85, 0x4F, 0x0B), new Color(0x99, 0x35, 0x56),
            new Color(0x53, 0x4A, 0xB7)
    };

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String code, LocalDateTime expireAt) {}

    /** 生成验证码：返回 captchaId 与 base64 图片（data URL，可直接放入 <img src>） */
    public Map<String, String> generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARS.charAt((int) (Math.random() * CHARS.length())));
        }
        String captchaId = java.util.UUID.randomUUID().toString().replace("-", "");
        store.put(captchaId, new Entry(code.toString(), LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));
        cleanup();
        String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(render(code.toString()));
        return Map.of("captchaId", captchaId, "image", image);
    }

    /**
     * 校验并销毁（一次性）：通过静默；失败抛业务异常并销毁该验证码（前端需刷新重试）。
     * 忽略大小写，提升可用性。
     */
    public void verify(String captchaId, String input) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new BusinessException("请先获取图形验证码");
        }
        Entry entry = store.remove(captchaId);
        if (entry == null) {
            throw new BusinessException("验证码已失效，请点击图片刷新后重试");
        }
        if (LocalDateTime.now().isAfter(entry.expireAt())) {
            throw new BusinessException("验证码已过期，请点击图片刷新后重试");
        }
        if (input == null || !entry.code().equalsIgnoreCase(input.trim())) {
            throw new BusinessException("验证码错误，请重新输入");
        }
    }

    /** 清理过期条目（每次生成时顺带清理，数据量小无需定时任务） */
    private void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        store.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(now));
    }

    /** 绘制扭曲验证码图片：随机彩色字符 + 旋转 + 多条交叉干扰线 */
    private byte[] render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 白底
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        // 干扰线（下层）：灰色细线随机交叉
        for (int i = 0; i < NOISE_LINES; i++) {
            g.setColor(new Color(0xB0 + (int) (Math.random() * 40),
                    0xB0 + (int) (Math.random() * 40), 0xB0 + (int) (Math.random() * 40)));
            g.setStroke(new BasicStroke(1f));
            g.drawLine((int) (Math.random() * WIDTH), (int) (Math.random() * HEIGHT),
                    (int) (Math.random() * WIDTH), (int) (Math.random() * HEIGHT));
        }
        // 逐字符绘制：随机颜色 + 随机上下浮动 + 轻微旋转
        int charWidth = (WIDTH - 20) / CODE_LENGTH;
        Font font = new Font("Arial", Font.BOLD, 30);
        for (int i = 0; i < code.length(); i++) {
            g.setColor(CHAR_COLORS[(int) (Math.random() * CHAR_COLORS.length)]);
            g.setFont(font);
            double theta = (Math.random() - 0.5) * 0.5; // ±14° 左右
            int x = 10 + i * charWidth + 2;
            int y = (HEIGHT - font.getSize()) / 2 + font.getSize() - 6 + (int) ((Math.random() - 0.5) * 8);
            Graphics2D cg = (Graphics2D) g.create();
            cg.rotate(theta, x + charWidth / 2.0, HEIGHT / 2.0);
            cg.drawString(String.valueOf(code.charAt(i)), x, y);
            cg.dispose();
        }
        // 干扰线（上层）：再次覆盖若干条，遮挡笔画
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(0x90 + (int) (Math.random() * 50),
                    0x90 + (int) (Math.random() * 50), 0x90 + (int) (Math.random() * 50)));
            g.drawLine((int) (Math.random() * WIDTH), (int) (Math.random() * HEIGHT),
                    (int) (Math.random() * WIDTH), (int) (Math.random() * HEIGHT));
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("验证码图片生成失败");
        }
    }
}
