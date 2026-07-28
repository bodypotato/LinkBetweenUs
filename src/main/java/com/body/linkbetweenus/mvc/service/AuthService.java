package com.body.linkbetweenus.mvc.service;

import com.body.linkbetweenus.dto.LoginRequest;
import com.body.linkbetweenus.dto.LoginResponse;
import com.body.linkbetweenus.dto.RegisterRequest;

public interface AuthService {

    /**
     * 用户注册
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
}
