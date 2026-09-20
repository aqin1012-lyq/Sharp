package com.sharp.service;

import com.sharp.entity.AppUser;
import com.sharp.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 注册 / 登录。密码用 BCrypt 存储，登录成功签发 JWT。 */
@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final JwtService jwtService;
    private final String inviteCode;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository userRepository, JwtService jwtService,
                       @Value("${auth.invite-code:}") String inviteCode) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.inviteCode = inviteCode;
    }

    /** 注册：校验邀请码与唯一用户名，返回 JWT。 */
    public String register(String username, String password, String code) {
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        if (isBlank(inviteCode) || !inviteCode.equals(code == null ? "" : code.trim())) {
            throw new IllegalArgumentException("邀请码不正确");
        }
        String uname = username.trim();
        if (uname.length() > 64) {
            throw new IllegalArgumentException("用户名过长（≤64）");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        if (userRepository.existsByUsername(uname)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        AppUser u = new AppUser();
        u.setUsername(uname);
        u.setPasswordHash(encoder.encode(password));
        userRepository.save(u);
        return jwtService.generate(uname);
    }

    /** 登录：校验密码，返回 JWT。 */
    public String login(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        AppUser u = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!encoder.matches(password, u.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return jwtService.generate(u.getUsername());
    }

    /**
     * 忘记密码：用邀请码重置。校验邀请码 + 用户名存在，写入新密码哈希，返回 JWT（重置后直接登录）。
     * 无邮件/短信基建，故用团队共享邀请码作为重置凭据。
     */
    public String resetPassword(String username, String code, String newPassword) {
        if (isBlank(username) || isBlank(newPassword)) {
            throw new IllegalArgumentException("用户名和新密码不能为空");
        }
        if (isBlank(inviteCode) || !inviteCode.equals(code == null ? "" : code.trim())) {
            throw new IllegalArgumentException("邀请码不正确");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码至少 6 位");
        }
        AppUser u = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        u.setPasswordHash(encoder.encode(newPassword));
        userRepository.save(u);
        return jwtService.generate(u.getUsername());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
