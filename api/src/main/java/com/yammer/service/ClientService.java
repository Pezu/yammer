package com.yammer.service;

import com.yammer.dto.ClientRequest;
import com.yammer.dto.ClientResponse;
import com.yammer.entity.ClientEntity;
import com.yammer.repository.ClientRepository;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import com.yammer.service.StorageService.StoredObject;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ClientService {

    private static final String LOGO_PREFIX = "clients";

    private final ClientRepository clientRepository;
    private final CurrentUserProvider currentUser;
    private final StorageService storageService;

    /** SUPER sees every client; any other user sees only the client they belong to. */
    public List<ClientResponse> list() {
        UserPrincipal me = currentUser.require();
        if (me.isSuper()) {
            return clientRepository.findAll(Sort.by("name")).stream().map(ClientResponse::from).toList();
        }
        if (me.clientId() == null) {
            return List.of();
        }
        return clientRepository.findById(me.clientId()).map(ClientResponse::from).stream().toList();
    }

    public ClientResponse create(ClientRequest request) {
        ClientEntity entity = new ClientEntity();
        apply(entity, request);
        return ClientResponse.from(clientRepository.save(entity));
    }

    public ClientResponse update(UUID id, ClientRequest request) {
        ClientEntity entity = clientRepository.findById(id).orElseThrow(() -> notFound(id));
        apply(entity, request);
        return ClientResponse.from(clientRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!clientRepository.existsById(id)) {
            throw notFound(id);
        }
        clientRepository.deleteById(id);
    }

    /** Replace the client's logo (deletes any previous object). */
    public ClientResponse uploadLogo(UUID id, MultipartFile file) {
        ClientEntity entity = clientRepository.findById(id).orElseThrow(() -> notFound(id));
        String object = storageService.uploadImage(LOGO_PREFIX, file);
        String previous = entity.getLogoObject();
        entity.setLogoObject(object);
        ClientEntity saved = clientRepository.save(entity);
        storageService.delete(previous); // best-effort cleanup of the old logo
        return ClientResponse.from(saved);
    }

    /** Remove the client's logo. */
    public ClientResponse deleteLogo(UUID id) {
        ClientEntity entity = clientRepository.findById(id).orElseThrow(() -> notFound(id));
        String previous = entity.getLogoObject();
        entity.setLogoObject(null);
        ClientEntity saved = clientRepository.save(entity);
        storageService.delete(previous);
        return ClientResponse.from(saved);
    }

    /** The client's logo bytes for serving, or 404 if it has none. */
    public StoredObject getLogo(UUID id) {
        ClientEntity entity = clientRepository.findById(id).orElseThrow(() -> notFound(id));
        if (entity.getLogoObject() == null) {
            throw notFound(id);
        }
        return storageService.get(entity.getLogoObject())
                .orElseThrow(() -> notFound(id));
    }

    private void apply(ClientEntity entity, ClientRequest request) {
        entity.setName(request.name().trim());
        entity.setPhone(trimToNull(request.phone()));
        entity.setEmail(trimToNull(request.email()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id);
    }
}
