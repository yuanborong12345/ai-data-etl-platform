package com.yuan.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuan.model.dto.user.UserAdminEditRequest;
import com.yuan.model.dto.user.UserLoginRequest;
import com.yuan.model.dto.user.UserQueryRequest;
import com.yuan.model.dto.user.UserUpdateRequest;
import com.yuan.model.entity.User;
import com.yuan.model.vo.LoginUserVO;
import com.yuan.model.vo.UserVO;

import java.util.List;


public interface UserService extends IService<User> {

    Long userRegister(String userAccount, String userPassword, String checkPassword);

    LoginUserVO userLogin(UserLoginRequest userLoginRequest);

    UserVO getCurrentUser(Long userId);

    List<UserVO> listUserByPage(UserQueryRequest queryRequest);

    Boolean isAdmin(Long userId);

    void logout(Long userId);

    void updateUser(UserUpdateRequest request, Long userId);

    void adminEditUser(UserAdminEditRequest request);
}
