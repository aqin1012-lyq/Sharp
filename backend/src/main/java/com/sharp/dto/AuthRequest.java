package com.sharp.dto;

import lombok.Data;

/** 登录 / 注册请求。inviteCode 仅注册用。 */
@Data
public class AuthRequest {
    private String username;
    private String password;
    private String inviteCode;
}
