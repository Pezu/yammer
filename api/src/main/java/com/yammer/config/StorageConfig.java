package com.yammer.config;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google Cloud Storage client.
 *
 * <p>When {@code gcs.endpoint} is set (local dev → fake-gcs-server emulator) it
 * connects there with no credentials. When blank (GCP) it uses Application Default
 * Credentials (workload identity on Cloud Run, or {@code GOOGLE_APPLICATION_CREDENTIALS}).
 */
@Configuration
public class StorageConfig {

    @Bean
    public Storage storage(
            @Value("${gcs.endpoint:}") String endpoint,
            @Value("${gcs.project-id:}") String projectId) {
        if (endpoint != null && !endpoint.isBlank()) {
            return StorageOptions.newBuilder()
                    .setHost(endpoint)
                    .setProjectId(projectId.isBlank() ? "yammer-local" : projectId)
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService();
        }
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (!projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        return builder.build().getService();
    }
}
