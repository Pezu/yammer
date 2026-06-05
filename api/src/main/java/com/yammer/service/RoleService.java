package com.yammer.service;

import com.yammer.dto.RoleRequest;
import com.yammer.dto.RoleResponse;
import com.yammer.entity.RoleEntity;
import com.yammer.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<RoleResponse> list() {
        return roleRepository.findAll(Sort.by("role")).stream().map(RoleResponse::from).toList();
    }

    public RoleResponse create(RoleRequest request) {
        String name = request.role().trim();
        if (roleRepository.existsByRoleIgnoreCase(name)) {
            throw conflict(name);
        }
        RoleEntity entity = new RoleEntity();
        entity.setRole(name);
        return RoleResponse.from(roleRepository.save(entity));
    }

    public RoleResponse update(UUID id, RoleRequest request) {
        RoleEntity entity = roleRepository.findById(id).orElseThrow(() -> notFound(id));
        String name = request.role().trim();
        if (!name.equalsIgnoreCase(entity.getRole()) && roleRepository.existsByRoleIgnoreCase(name)) {
            throw conflict(name);
        }
        entity.setRole(name);
        return RoleResponse.from(roleRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw notFound(id);
        }
        roleRepository.deleteById(id);
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + id);
    }

    private ResponseStatusException conflict(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists: " + name);
    }
}
