package com.yammer.service;

import com.yammer.dto.VatTypeRequest;
import com.yammer.dto.VatTypeResponse;
import com.yammer.entity.VatTypeEntity;
import com.yammer.repository.VatTypeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class VatTypeService {

    private final VatTypeRepository vatTypeRepository;

    public List<VatTypeResponse> list() {
        return vatTypeRepository.findAll(Sort.by("value")).stream().map(VatTypeResponse::from).toList();
    }

    public VatTypeResponse create(VatTypeRequest request) {
        VatTypeEntity entity = new VatTypeEntity();
        apply(entity, request);
        return VatTypeResponse.from(vatTypeRepository.save(entity));
    }

    public VatTypeResponse update(UUID id, VatTypeRequest request) {
        VatTypeEntity entity = vatTypeRepository.findById(id).orElseThrow(() -> notFound(id));
        apply(entity, request);
        return VatTypeResponse.from(vatTypeRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!vatTypeRepository.existsById(id)) {
            throw notFound(id);
        }
        vatTypeRepository.deleteById(id);
    }

    private void apply(VatTypeEntity entity, VatTypeRequest request) {
        entity.setValue(request.value());
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "VAT type not found: " + id);
    }
}
