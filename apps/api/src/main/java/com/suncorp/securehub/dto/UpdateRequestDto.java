package com.suncorp.securehub.dto;

import com.suncorp.securehub.entity.SupportRequest.RequestStatus;
import lombok.Data;

@Data
public class UpdateRequestDto {
    private RequestStatus status;
    private Long assignedToId;
}
