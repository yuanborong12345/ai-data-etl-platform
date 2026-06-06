package com.yuan.filter;

import com.yuan.utils.UserContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
/**
 * @author yuan
 */
public class UserContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String role = req.getHeader("X-USER-ROLE");
        UserContext.setRole(role);
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}