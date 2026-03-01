package com.suncorp.securehub.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.attachments")
public class AttachmentProperties {

    @NotBlank
    private String bucketName = "securehub-attachments-local";

    @NotBlank
    private String awsRegion = "ap-southeast-2";

    private String awsS3Endpoint;

    @Min(1)
    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    @Min(1)
    private int requestMaxCount = 10;

    @Min(1)
    private int commentMaxCount = 5;

    @NotNull
    private Duration uploadUrlTtl = Duration.ofMinutes(5);

    @NotNull
    private Duration downloadUrlTtl = Duration.ofMinutes(5);

    @NotEmpty
    private Set<String> allowedMimeTypes = new LinkedHashSet<>(
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp",
                    "application/pdf",
                    "text/plain",
                    "text/csv"
            )
    );
}
