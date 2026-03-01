package com.suncorp.securehub.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(AttachmentProperties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(AttachmentProperties properties) {
        var builder = S3Client.builder()
                .credentialsProvider(credentialsProvider(properties))
                .region(Region.of(properties.getAwsRegion()));

        if (StringUtils.hasText(properties.getAwsS3Endpoint())) {
            builder.endpointOverride(URI.create(properties.getAwsS3Endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(AttachmentProperties properties) {
        var builder = S3Presigner.builder()
                .credentialsProvider(credentialsProvider(properties))
                .region(Region.of(properties.getAwsRegion()));

        if (StringUtils.hasText(properties.getAwsS3Endpoint())) {
            builder.endpointOverride(URI.create(properties.getAwsS3Endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }

    private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider(
            AttachmentProperties properties) {
        if (StringUtils.hasText(properties.getAwsAccessKeyId())
                && StringUtils.hasText(properties.getAwsSecretAccessKey())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getAwsAccessKeyId(),
                    properties.getAwsSecretAccessKey()
            ));
        }

        return DefaultCredentialsProvider.create();
    }
}
