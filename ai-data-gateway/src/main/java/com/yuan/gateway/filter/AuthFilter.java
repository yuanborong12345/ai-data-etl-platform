package com.yuan.gateway.filter;

import cn.hutool.core.text.AntPathMatcher;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yuan.common.ErrorCode;
import com.yuan.constant.RedisKeyPrefix;
import com.yuan.model.dto.gateway.GatewaySessionDTO;
import com.yuan.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 权限拦截器
 */
@Component
@Slf4j
public class AuthFilter implements GlobalFilter, Ordered {

    @Resource
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Resource
    private JwtUtils jwtUtils;

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/doc.html",
            "/webjars/**",
            "/swagger-resources/**"
    );

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        //1. 白名单校验
        for (String url : WHITE_LIST) {
            // 使用全局的 matcher 进行匹配
            if (ANT_PATH_MATCHER.match(url, path)) {
                log.debug("白名单放行路径: {}", path);
                return chain.filter(exchange);
            }
        }
        //2. 提取Header 中的 Token 凭证
        String authorization = request.getHeaders().getFirst("Authorization");
        if (StrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.NOT_LOGIN_ERROR);
        }
        String token = authorization.substring(7);
        try {
            //3. 解密并提取上下文
            Claims claims = jwtUtils.parseToken(token);
            String userId = claims.getSubject();
            String userRole = claims.get("role", String.class);
            if (StrUtil.isBlank(userId) || StrUtil.isBlank(userRole)) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.NOT_LOGIN_ERROR);
            }
            // 4. 查动态 Session
            String redisKey = RedisKeyPrefix.SESSION_PREFIX + userId;
            return reactiveStringRedisTemplate.opsForValue().get(redisKey)
                    .flatMap(sessionJson -> {
                        // Redis 中无此 Session，说明已经过期或者下线
                        if (StrUtil.isBlank(sessionJson)) {
                            return onError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.NOT_LOGIN_ERROR);
                        }
                        //转换Session 结构
                        GatewaySessionDTO session = JSONUtil.toBean(sessionJson, GatewaySessionDTO.class);

                        // 顶号校验：传上来的 Token 与 Redis 最新的不一致
                        if (!token.equals(session.getToken())) {
                            return onError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.NOT_LOGIN_ERROR);
                        }

                        // 状态校验：被管理员秒级封号
                        if (session.getStatus() == 1) {
                            return onError(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN_ERROR);
                        }

                        // Token 自动续期逻辑（滑动窗口）
                        reactiveStringRedisTemplate.getExpire(redisKey).subscribe(duration -> {
                            if (duration != null && duration.getSeconds() < 900) {
                                reactiveStringRedisTemplate.expire(redisKey, Duration.ofMinutes(30)).subscribe();
                                log.info("用户 [{}] 的分布式 Session 触发无感自动续期", userId);
                            }
                        });

                        // 5. 网关请求头明文传递给下游微服务
                        ServerHttpRequest mutateRequest = request.mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Role", userRole)
                                .build();

                        // 放行，透传修改后的 request
                        return chain.filter(exchange.mutate().request(mutateRequest).build());
                    }).switchIfEmpty(onError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.NOT_LOGIN_ERROR));
        } catch (Exception e) {
            log.error("网关解析 JWT 签名失败，路径: {}, 异常原因: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.NOT_LOGIN_ERROR);
        }
    }
    /**
     * 构筑响应式标准的错误输出返回 (JSON 格式)
     */
    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String responseJson = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}", errorCode.getCode(), errorCode.getMessage());
        DataBuffer buffer = response.bufferFactory().wrap(responseJson.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
