package com.yuan.user.controller;

import com.yuan.annotation.AuthCheck;
import com.yuan.common.BaseResponse;
import com.yuan.common.ErrorCode;
import com.yuan.common.ResultUtils;
import com.yuan.constant.UserConstant;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.user.UserAdminEditRequest;
import com.yuan.model.dto.user.UserLoginRequest;
import com.yuan.model.dto.user.UserQueryRequest;
import com.yuan.model.dto.user.UserRegisterRequest;
import com.yuan.model.dto.user.UserUpdateRequest;
import com.yuan.model.vo.LoginUserVO;
import com.yuan.model.vo.UserVO;
import com.yuan.user.service.UserService;
import com.yuan.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private JwtUtils jwtUtils;

    @PostMapping("/register")
    public BaseResponse<Long> register(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        Long userId = userService.userRegister(
                userRegisterRequest.getUserAccount(),
                userRegisterRequest.getUserPassword(),
                userRegisterRequest.getCheckPassword());
        return ResultUtils.success(userId);
    }

    @PostMapping("/login")
    public BaseResponse<LoginUserVO> login(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        LoginUserVO vo = userService.userLogin(userLoginRequest);
        return ResultUtils.success(vo);
    }

    @GetMapping("/current")
    public BaseResponse<UserVO> current(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromToken(authHeader);
        UserVO vo = userService.getCurrentUser(userId);
        return ResultUtils.success(vo);
    }

    @PostMapping("/logout")
    public BaseResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromToken(authHeader);
        userService.logout(userId);
        return ResultUtils.success(null);
    }

    @PostMapping("/update")
    public BaseResponse<Void> update(@RequestHeader("Authorization") String authHeader,
                                     @Valid @RequestBody UserUpdateRequest request) {
        Long userId = getUserIdFromToken(authHeader);
        userService.updateUser(request, userId);
        return ResultUtils.success(null);
    }

    @PostMapping("/admin/edit")
    @AuthCheck(mustRole = UserConstant.ROLE_ADMIN)
    public BaseResponse<Void> adminEdit(@Valid @RequestBody UserAdminEditRequest request) {
        userService.adminEditUser(request);
        return ResultUtils.success(null);
    }

    @PostMapping("/list")
    @AuthCheck(mustRole = UserConstant.ROLE_ADMIN)
    public BaseResponse<List<UserVO>> list(@RequestBody UserQueryRequest request) {
        List<UserVO> list = userService.listUserByPage(request);
        return ResultUtils.success(list);
    }

    private Long getUserIdFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtUtils.parseToken(token);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已过期，请重新登录");
        }
        return Long.valueOf(claims.getSubject());
    }
}
