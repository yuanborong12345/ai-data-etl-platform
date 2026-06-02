package com.yuan.serviceclient.feign;

import com.yuan.common.api.Result;
import com.yuan.model.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ai-data-user", path = "/user")
public interface UserFeignClient {

    @GetMapping("/{id}")
    Result<UserDTO> getUserById(@PathVariable("id") Long id);
}
