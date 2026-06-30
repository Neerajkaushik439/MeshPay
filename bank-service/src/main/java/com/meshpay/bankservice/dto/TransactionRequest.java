package com.meshpay.bankservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotBlank(message = "Sender UPI ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$",
        message = "Sender UPI ID must be in a valid format (e.g. user@okbank)"
    )
    private String senderUpiId;

    @NotBlank(message = "Receiver UPI ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$",
        message = "Receiver UPI ID must be in a valid format (e.g. user@okbank)"
    )
    private String receiverUpiId;

    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "0.01", message = "Transaction amount must be at least 0.01")
    private BigDecimal amount;
}
