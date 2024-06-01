/*
 * Copyright © 2023-2024 Rohit Parihar and Bloggios
 * All rights reserved.
 * This software is the property of Rohit Parihar and is protected by copyright law.
 * The software, including its source code, documentation, and associated files, may not be used, copied, modified, distributed, or sublicensed without the express written consent of Rohit Parihar.
 * For licensing and usage inquiries, please contact Rohit Parihar at rohitparih@gmail.com, or you can also contact support@bloggios.com.
 * This software is provided as-is, and no warranties or guarantees are made regarding its fitness for any particular purpose or compatibility with any specific technology.
 * For license information and terms of use, please refer to the accompanying LICENSE file or visit http://www.apache.org/licenses/LICENSE-2.0.
 * Unauthorized use of this software may result in legal action and liability for damages.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloggios.authenticationconfig.authentication;

import com.bloggios.authenticationconfig.constants.ServiceConstants;
import com.bloggios.authenticationconfig.payload.AuthenticatedUser;
import com.bloggios.authenticationconfig.payload.JwtErrorResponse;
import com.bloggios.authenticationconfig.properties.SecurityConfigProperties;
import com.bloggios.authenticationconfig.util.IpUtils;
import com.bloggios.authenticationconfig.util.JwtDecoderUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.context.DelegatingApplicationListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Owner - Rohit Parihar
 * Author - rohit
 * Project - bloggios-config-auth-provider
 * Package - com.bloggios.authenticationconfig.authentication
 * Created_on - 14 December-2023
 * Created_at - 19 : 12
 */

@Component
public class JwtTokenValidationFilter extends OncePerRequestFilter {

    private final JwtDecoderUtil jwtDecoderUtil;
    private final JwtDecoder jwtDecoder;
    private final SecurityConfigProperties securityConfigProperties;
    private final AntPathMatcher antPathMatcher;

    public JwtTokenValidationFilter(
            JwtDecoderUtil jwtDecoderUtil,
            JwtDecoder jwtDecoder,
            SecurityConfigProperties securityConfigProperties,
            AntPathMatcher antPathMatcher
    ) {
        this.jwtDecoderUtil = jwtDecoderUtil;
        this.jwtDecoder = jwtDecoder;
        this.securityConfigProperties = securityConfigProperties;
        this.antPathMatcher = antPathMatcher;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String breadcrumbId = extractBreadcrumbId(request);
            MDC.put(ServiceConstants.BREADCRUMB_ID, breadcrumbId);
            String token = extractToken(request);
            List<String> excludePaths = securityConfigProperties.getExclude().getPaths();
            List<String> cookiePaths = securityConfigProperties.getCookie().getPaths();
            boolean isExcludePath = excludePaths.stream().anyMatch(e -> antPathMatcher.match(e, request.getRequestURI()));
            boolean isCookiePath = false;
            if (!CollectionUtils.isEmpty(cookiePaths)) {
                isCookiePath = cookiePaths.stream().anyMatch(e -> antPathMatcher.match(e, request.getRequestURI()));
            }
            if (!isExcludePath && !isCookiePath) {
                if (token != null) {
                    try {
                        jwtDecoder.decode(token);
                        validateClientIp(token, request, response);
                    } catch (JwtValidationException exception) {
                        Collection<OAuth2Error> errors = exception.getErrors();
                        boolean isExpired = false;
                        for (OAuth2Error error : errors) {
                            if (error.getDescription().contains("expired")) {
                                isExpired = true;
                                break;
                            }
                        }
                        response.setStatus(isExpired ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value());
                        response.setContentType("application/json");
                        OutputStream output = response.getOutputStream();
                        ObjectMapper mapper = new ObjectMapper();
                        JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                                .builder()
                                .message(isExpired ? "JWT token is Expired" : exception.getMessage())
                                .isExpired(isExpired)
                                .build();
                        mapper.writeValue(output, jwtErrorResponse);
                        output.flush();
                        return;
                    } catch (BadJwtException exception) {
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType("application/json");
                        OutputStream output = response.getOutputStream();
                        ObjectMapper mapper = new ObjectMapper();
                        JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                                .builder()
                                .message(exception.getMessage())
                                .build();
                        mapper.writeValue(output, jwtErrorResponse);
                        output.flush();
                        return;
                    }
                    addAuthentication(request, token);
                }
            } else if (isCookiePath) {
                logger.info("Initiated Cookie Authentication of Incoming Request");
                Optional<Cookie> cookieOptional = getCookie(request, securityConfigProperties.getCookie().getCookieName());
                if (cookieOptional.isEmpty()) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    OutputStream output = response.getOutputStream();
                    ObjectMapper mapper = new ObjectMapper();
                    JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                            .builder()
                            .message("Authentication cookie is not present the request")
                            .build();
                    mapper.writeValue(output, jwtErrorResponse);
                    output.flush();
                    return;
                }
                String cookieToken = cookieOptional.get().getValue();
                try {
                    jwtDecoder.decode(cookieToken);
                } catch (JwtValidationException exception) {
                    Collection<OAuth2Error> errors = exception.getErrors();
                    boolean isExpired = false;
                    for (OAuth2Error error : errors) {
                        if (error.getDescription().contains("expired")) {
                            isExpired = true;
                            break;
                        }
                    }
                    response.setStatus(isExpired ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    OutputStream output = response.getOutputStream();
                    ObjectMapper mapper = new ObjectMapper();
                    JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                            .builder()
                            .message(isExpired ? "Cookie Token is Expired in cookie" : exception.getMessage())
                            .isExpired(isExpired)
                            .build();
                    mapper.writeValue(output, jwtErrorResponse);
                    output.flush();
                    return;
                } catch (BadJwtException exception) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    OutputStream output = response.getOutputStream();
                    ObjectMapper mapper = new ObjectMapper();
                    JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                            .builder()
                            .message(exception.getMessage())
                            .build();
                    mapper.writeValue(output, jwtErrorResponse);
                    output.flush();
                    return;
                }
                if (Objects.isNull(jwtDecoderUtil.extractTokenType(cookieToken))) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    OutputStream output = response.getOutputStream();
                    ObjectMapper mapper = new ObjectMapper();
                    JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                            .builder()
                            .message("Unable to extract token type from Cookie Token")
                            .build();
                    mapper.writeValue(output, jwtErrorResponse);
                    output.flush();
                    return;
                }
                if (!jwtDecoderUtil.extractTokenType(cookieToken).equals("cookie-token")) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    OutputStream output = response.getOutputStream();
                    ObjectMapper mapper = new ObjectMapper();
                    JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                            .builder()
                            .message("Token type must be cookie for validation")
                            .build();
                    mapper.writeValue(output, jwtErrorResponse);
                    output.flush();
                    return;
                }
                addAuthentication(request, cookieToken);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ServiceConstants.BREADCRUMB_ID);
        }
    }

    private String extractBreadcrumbId(HttpServletRequest request) {
        String breadcrumbId;
        if (StringUtils.hasText(request.getHeader(ServiceConstants.BREADCRUMB_ID))) {
            breadcrumbId = request.getHeader(ServiceConstants.BREADCRUMB_ID);
        } else {
            breadcrumbId = UUID.randomUUID().toString();
        }
        return breadcrumbId;
    }

    private void validateClientIp(String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String clientIpFromToken = jwtDecoderUtil.extractClientIp(token);
        String remoteAddress = IpUtils.getRemoteAddress(request);
        if (!clientIpFromToken.equals(ServiceConstants.BYPASSED_IP) && !clientIpFromToken.equals(remoteAddress)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            OutputStream output = response.getOutputStream();
            ObjectMapper mapper = new ObjectMapper();
            JwtErrorResponse jwtErrorResponse = JwtErrorResponse
                    .builder()
                    .message("Not allowed to use authentication token generated on other device")
                    .build();
            mapper.writeValue(output, jwtErrorResponse);
            output.flush();
        }
    }

    private void addAuthentication(HttpServletRequest request, String token) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthenticatedUser authenticatedUser = new AuthenticatedUser();
            String userId = jwtDecoderUtil.extractUserId(token);
            String email = jwtDecoderUtil.extractEmail(token);
            String username = jwtDecoderUtil.extractUsername(token);
            Collection<? extends GrantedAuthority> grantedAuthorities = jwtDecoderUtil.extractAuthorities(token);
            authenticatedUser.setUserId(userId);
            authenticatedUser.setEmail(email);
            authenticatedUser.setAuthorities(grantedAuthorities);
            authenticatedUser.setClientIp(jwtDecoderUtil.extractClientIp(token));
            authenticatedUser.setUsername(username);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(authenticatedUser, null, grantedAuthorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private String extractToken(HttpServletRequest httpServletRequest) {
        String header = httpServletRequest.getHeader(ServiceConstants.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (Objects.nonNull(cookies) && cookies.length > 0) {
            return Arrays
                    .stream(cookies)
                    .filter(cookie -> Objects.equals(cookie.getName(), name))
                    .findFirst();
        }
        return Optional.empty();
    }
}
