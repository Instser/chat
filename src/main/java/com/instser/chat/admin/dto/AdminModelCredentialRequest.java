package com.instser.chat.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminModelCredentialRequest {

    @Size(max = 500)
    private String baseUrl;

    @NotBlank
    @Size(max = 1000)
    private String apiKey;

    @NotBlank
    @Size(max = 500)
    private String reason;
}
