package com.yuan.user.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuan.common.ErrorCode;
import com.yuan.constant.RedisKeyPrefix;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.user.UserAdminEditRequest;
import com.yuan.model.dto.user.UserLoginRequest;
import com.yuan.model.dto.user.UserQueryRequest;
import com.yuan.model.dto.user.UserSessionDTO;
import com.yuan.model.dto.user.UserUpdateRequest;
import com.yuan.model.entity.User;
import com.yuan.model.vo.LoginUserVO;
import com.yuan.model.vo.UserVO;
import com.yuan.user.mapper.UserMapper;
import com.yuan.user.service.UserService;
import com.yuan.utils.JwtUtils;
import com.yuan.utils.UserContext;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtils jwtUtils;

    private static final String SALT = "ai_data_user_yuan_salt";

    /**
     * 用户注册
     */
    @Override
    public Long userRegister(String userAccount, String userPassword, String checkPassword) {
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, userAccount);
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(DigestUtil.sha256Hex(userPassword + SALT));
        user.setUserName(userAccount);
        user.setUserRole("user");
        baseMapper.insert(user);
        return user.getId();
    }

    /**
     * 用户登录
     */
    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest) {
        String userPassword = userLoginRequest.getUserPassword();
        String userAccount = userLoginRequest.getUserAccount();

        // 1. 查询数据库看账号是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, userAccount);
        User user = baseMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.LOGIN_ACCOUNT_NOT_EXIST);
        }

        // 2. 验证密码
        String encrypted = DigestUtil.sha256Hex(userPassword + SALT);
        if (!encrypted.equals(user.getUserPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_ACCOUNT_PASSWORD_ERROR);
        }

        //3. 账号状态检查
        Integer status = user.getStatus();
        if (status != null && status.equals(1)) {
            throw new BusinessException(ErrorCode.LOGIN_ACCOUNT_STATUS_EXCEPTION);
        }
        //4. 生成Token
        String token = jwtUtils.generateToken(user.getId(), user.getUserAccount(),user.getUserRole());
        //5.组装结构Session
        UserSessionDTO session = new UserSessionDTO();
        session.setId(user.getId());
        session.setUserAccount(user.getUserAccount());
        session.setUserName(user.getUserName());
        session.setUserRole(user.getUserRole());
        session.setStatus(user.getStatus());
        session.setToken(token);
        session.setLoginTime(System.currentTimeMillis());
        String key = RedisKeyPrefix.SESSION_PREFIX + user.getId();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(session), 30, TimeUnit.MINUTES);
        LoginUserVO vo = new LoginUserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken(token);
        return vo;
    }

    /**
     * 根据用户id获取当前用户信息（脱敏）
     */
    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * 分页获取用户
     */
    @Override
    public List<UserVO> listUserByPage(UserQueryRequest queryRequest) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getId() != null) {
            wrapper.eq(User::getId, queryRequest.getId());
        }
        if (queryRequest.getUserAccount() != null) {
            wrapper.like(User::getUserAccount, queryRequest.getUserAccount());
        }
        if (queryRequest.getUserName() != null) {
            wrapper.like(User::getUserName, queryRequest.getUserName());
        }
        if (queryRequest.getUserRole() != null) {
            wrapper.eq(User::getUserRole, queryRequest.getUserRole());
        }
        if (queryRequest.getStatus() != null) {
            wrapper.eq(User::getStatus, queryRequest.getStatus());
        }

        Page<User> page = baseMapper.selectPage(
                new Page<>(queryRequest.getPageNum(), queryRequest.getPageSize()), wrapper);

        return page.getRecords().stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 判断是否为管理员
     */
    @Override
    public Boolean isAdmin(Long userId) {
        String role = UserContext.getRole();
        if(role.equals("admin")){
            return true;
        }
        return false;
    }

    @Override
    public void logout(Long userId) {
        String key = RedisKeyPrefix.SESSION_PREFIX + userId;
        stringRedisTemplate.delete(key);
    }

    @Override
    public void updateUser(UserUpdateRequest request, Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        if (request.getUserName() != null) {
            user.setUserName(request.getUserName());
        }
        if (request.getUserAvatar() != null) {
            user.setUserAvatar(request.getUserAvatar());
        }
        if (request.getUserProfile() != null) {
            user.setUserProfile(request.getUserProfile());
        }
        baseMapper.updateById(user);
    }

    @Override
    public void adminEditUser(UserAdminEditRequest request) {
        User user = baseMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        if (request.getUserName() != null) {
            user.setUserName(request.getUserName());
        }
        if (request.getUserAvatar() != null) {
            user.setUserAvatar(request.getUserAvatar());
        }
        if (request.getUserProfile() != null) {
            user.setUserProfile(request.getUserProfile());
        }
        if (request.getUserRole() != null) {
            user.setUserRole(request.getUserRole());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        baseMapper.updateById(user);
    }
}
