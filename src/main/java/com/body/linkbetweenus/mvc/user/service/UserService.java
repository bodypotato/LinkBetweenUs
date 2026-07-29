package com.body.linkbetweenus.mvc.user.service;

import com.body.linkbetweenus.dto.UserCacheVo;

public interface UserService {

    /**
     * 查询个人信息（不含密码）
     */
    UserCacheVo getInfo(String account);

    /**
     * 修改昵称
     */
    void updateName(String account, String newName);
}
