package com.yammer.controller;

import com.yammer.dto.ClientRequest;
import com.yammer.dto.ClientResponse;
import com.yammer.service.ClientService;
import com.yammer.service.StorageService.StoredObject;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public List<ClientResponse> list() {
        return clientService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER')")
    public ClientResponse create(@Valid @RequestBody ClientRequest request) {
        return clientService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER')")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER')")
    public void delete(@PathVariable UUID id) {
        clientService.delete(id);
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER')")
    public ClientResponse uploadLogo(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return clientService.uploadLogo(id, file);
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("hasRole('SUPER')")
    public ClientResponse deleteLogo(@PathVariable UUID id) {
        return clientService.deleteLogo(id);
    }

    /** Serves the client's logo image. Public (logos aren't secret) so {@code <img>} can load it. */
    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID id) {
        StoredObject logo = clientService.getLogo(id);
        // The web client version-busts the URL on upload (logoVersion query param) and the stored
        // object key changes per upload, so the bytes are safely cacheable for a day.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(logo.data());
    }
}
