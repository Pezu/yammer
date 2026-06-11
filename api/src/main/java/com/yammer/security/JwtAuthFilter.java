package com.yammer.security;

import com.yammer.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));

                List<?> rawRoles = claims.get("roles", List.class);
                List<String> roles = rawRoles == null
                        ? List.of()
                        : rawRoles.stream().map(String::valueOf).toList();

                String clientIdClaim = claims.get("clientId", String.class);
                UUID clientId = clientIdClaim == null ? null : UUID.fromString(clientIdClaim);

                UserPrincipal principal = new UserPrincipal(claims.getSubject(), clientId, roles);
                // ROLE_ prefix so @PreAuthorize("hasRole('SUPER')") works.
                var authorities = roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();

                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // Invalid/expired token — leave the request unauthenticated.
                SecurityContextHolder.clearContext();
                log.debug("Rejected bearer token: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
