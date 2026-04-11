package com.smart.exam.auth.config;

import com.smart.exam.auth.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PasswordStorageGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordStorageGuard.class);
    private static final int SAMPLE_LIMIT = 10;

    private final SysUserMapper sysUserMapper;
    private final boolean allowLegacyPlainPassword;

    public PasswordStorageGuard(SysUserMapper sysUserMapper,
                                @Value("${smart-exam.auth.security.allow-legacy-plain-password:false}")
                                boolean allowLegacyPlainPassword) {
        this.sysUserMapper = sysUserMapper;
        this.allowLegacyPlainPassword = allowLegacyPlainPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (allowLegacyPlainPassword) {
            throw new IllegalStateException(
                    "ALLOW_LEGACY_PLAIN_PASSWORD is no longer supported after password cutover. "
                            + "Run the offline migration tool first and keep the flag disabled."
            );
        }

        long legacyUserCount = sysUserMapper.countLegacyPasswordUsers();
        if (legacyUserCount > 0) {
            List<String> sampleUsernames = sysUserMapper.selectLegacyPasswordUsernames(SAMPLE_LIMIT);
            throw new IllegalStateException(
                    "Detected " + legacyUserCount + " legacy plain-password records in user_db.sys_user. "
                            + "Run scripts/security/migrate-legacy-passwords.* before starting auth-service. "
                            + "Sample usernames: " + sampleUsernames
            );
        }

        log.info("Password storage verification passed: all sys_user records use BCrypt.");
    }
}
