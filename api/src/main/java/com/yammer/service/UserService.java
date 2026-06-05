package com.yammer.service;

import com.yammer.dto.UserRequest;
import com.yammer.dto.UserResponse;
import com.yammer.entity.UserEntity;
import com.yammer.repository.ClientRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SUPER = "SUPER";

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final CurrentUserProvider currentUser;

    /** SUPER sees all users; everyone else sees only users in their own client. */
    public List<UserResponse> list() {
        UserPrincipal me = currentUser.require();
        List<UserEntity> users = me.isSuper()
                ? userRepository.findAll(Sort.by("username"))
                : me.clientId() == null
                        ? List.of()
                        : userRepository.findByClientIdOrderByUsername(me.clientId());
        return users.stream().map(UserResponse::from).toList();
    }

    public UserResponse create(UserRequest request) {
        UserPrincipal me = currentUser.require();
        String username = request.username().trim();
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw conflict(username);
        }
        List<String> roles = sanitizeRoles(me, request.roles());
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPassword(md5(request.password()));
        applyProfile(entity, me, roles, request);
        return UserResponse.from(userRepository.save(entity));
    }

    public UserResponse update(UUID id, UserRequest request) {
        UserPrincipal me = currentUser.require();
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> notFound(id));
        // A non-SUPER operator may only touch users inside their own client, and never SUPER users.
        if (!me.isSuper()
                && (!Objects.equals(entity.getClientId(), me.clientId()) || entity.getRoles().contains(SUPER))) {
            throw notFound(id);
        }
        String username = request.username().trim();
        if (!username.equalsIgnoreCase(entity.getUsername())
                && userRepository.existsByUsernameIgnoreCase(username)) {
            throw conflict(username);
        }
        List<String> roles = sanitizeRoles(me, request.roles());
        entity.setUsername(username);
        if (request.password() != null && !request.password().isBlank()) {
            entity.setPassword(md5(request.password()));
        }
        applyProfile(entity, me, roles, request);
        return UserResponse.from(userRepository.save(entity));
    }

    public void delete(UUID id) {
        UserPrincipal me = currentUser.require();
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> notFound(id));
        if (!me.isSuper()
                && (!Objects.equals(entity.getClientId(), me.clientId()) || entity.getRoles().contains(SUPER))) {
            throw notFound(id);
        }
        userRepository.delete(entity);
    }

    private void applyProfile(UserEntity entity, UserPrincipal me, List<String> roles, UserRequest request) {
        entity.setPhone(trimToNull(request.phone()));
        entity.setEmail(trimToNull(request.email()));
        entity.setRoles(roles);
        entity.setClientId(resolveClient(me, roles, request.clientId()));
    }

    /** A non-SUPER operator cannot grant the SUPER role. */
    private List<String> sanitizeRoles(UserPrincipal me, List<String> requested) {
        List<String> roles = requested == null ? new ArrayList<>() : new ArrayList<>(requested);
        if (!me.isSuper() && roles.contains(SUPER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only SUPER users can grant the SUPER role");
        }
        return roles;
    }

    /**
     * SUPER users have no client. For every other user a client is required:
     * a SUPER operator may pick any existing client; a non-SUPER operator is forced
     * to their own client (the requested value is ignored).
     */
    private UUID resolveClient(UserPrincipal me, List<String> roles, UUID requested) {
        if (roles.contains(SUPER)) {
            return null;
        }
        if (!me.isSuper()) {
            if (me.clientId() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not linked to a client");
            }
            return me.clientId();
        }
        if (requested == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A client is required for non-SUPER users");
        }
        if (!clientRepository.existsById(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown client: " + requested);
        }
        return requested;
    }

    private String md5(String raw) {
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id);
    }

    private ResponseStatusException conflict(String username) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + username);
    }
}
