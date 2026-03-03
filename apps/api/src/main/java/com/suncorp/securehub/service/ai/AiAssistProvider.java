package com.suncorp.securehub.service.ai;

import com.suncorp.securehub.dto.AiContextDto;
import com.suncorp.securehub.dto.AiDraftResponseDto;
import com.suncorp.securehub.dto.AiSuggestTagsResponseDto;
import com.suncorp.securehub.dto.AiSummarizeResponseDto;

public interface AiAssistProvider {
    AiSummarizeResponseDto summarize(AiContextDto context);

    AiSuggestTagsResponseDto suggestTags(AiContextDto context);

    AiDraftResponseDto draftResponse(AiContextDto context);

    String getProviderName();

    String getModelId();
}
