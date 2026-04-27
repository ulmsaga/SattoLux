package com.saga.sattolux.core.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            if (jwtUtil.isValid(token)) {
                Claims claims = jwtUtil.parse(token);
                if ("access".equals(claims.get("type"))) {
                    Long userSeq = Long.parseLong(claims.getSubject());
                    String userId = (String) claims.get("userId");
                    Object roleClaim = claims.get("role");
                    String roleCode = roleClaim == null ? "USER" : String.valueOf(roleClaim);
                    var auth = new UsernamePasswordAuthenticationToken(
                            userSeq, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + roleCode))
                    );
                    auth.setDetails(userId);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        if ("/api/auth/sse".equals(request.getRequestURI())) {
            String accessToken = request.getParameter("accessToken");
            if (accessToken != null && !accessToken.isBlank()) {
                return accessToken;
            }
        }

        return null;
    }
}
