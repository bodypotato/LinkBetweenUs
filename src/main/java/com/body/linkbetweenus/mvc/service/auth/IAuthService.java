package com.body.linkbetweenus.mvc.service.auth;

import com.body.linkbetweenus.dto.LoginRequest;
import com.body.linkbetweenus.dto.LoginResponse;
import com.body.linkbetweenus.dto.RegisterRequest;

public interface IAuthService {

    /**
     * 用户注册
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
}
