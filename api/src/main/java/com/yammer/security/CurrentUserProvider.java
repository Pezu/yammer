package com.yammer.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Single access point for the authenticated {@link UserPrincipal}. */
@Component
public class CurrentUserProvider {

    /** The current principal, or 401 if the request is not authenticated. */
    public UserPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
}
