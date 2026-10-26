package com.bunny.rtt_api.service.auth;

import com.bunny.rtt_api.dto.request.LoginRequest;
import com.bunny.rtt_api.dto.response.SessionResponse;
import com.bunny.rtt_api.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    SessionResponse login(LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse);

    SessionResponse refresh(HttpServletRequest servletRequest, HttpServletResponse servletResponse);

    void logout(HttpServletResponse servletResponse);

    UserResponse getMe();
}
