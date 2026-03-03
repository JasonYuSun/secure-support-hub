package com.suncorp.securehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_assist_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAssistRun {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "action_type", nullable = false)
    private String actionType; // SUMMARIZE, SUGGEST_TAGS, DRAFT_RESPONSE

    @Column(name = "provider", nullable = false)
    private String provider; // stub, bedrock

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    private String inputSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload", columnDefinition = "jsonb")
    private String outputPayload;

    @Column(name = "status", nullable = false)
    private String status; // SUCCESS, FAILED

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
