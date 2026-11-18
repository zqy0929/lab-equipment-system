package com.lab.equipment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.equipment.entity.User;
import com.lab.equipment.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@SpringBootApplication
@EnableScheduling
public class LabEquipmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabEquipmentApplication.class, args);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 首次启动时自动创建管理员账号 admin / admin123 */
    @Component
    public static class AdminInitializer implements ApplicationRunner {
        private final UserMapper userMapper;
        private final BCryptPasswordEncoder encoder;

        public AdminInitializer(UserMapper userMapper, BCryptPasswordEncoder encoder) {
            this.userMapper = userMapper;
            this.encoder = encoder;
        }

        @Override
        public void run(ApplicationArguments args) {
            Long count = userMapper.selectCount(
                    new QueryWrapper<User>().eq("username", "admin"));
            if (count == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin123"));
                admin.setRealName("系统管理员");
                admin.setRole("ADMIN");
                admin.setStatus(1);
                admin.setCreateTime(LocalDateTime.now());
                userMapper.insert(admin);
                System.out.println(">>> 已初始化管理员账号：admin / admin123");
            }
        }
    }
}
