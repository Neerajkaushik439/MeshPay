package com.meshpay.bankservice.dto;

import com.meshpay.bankservice.entity.AccountStatus;
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
public class AccountRequest {

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "Account holder name is required")
    private String accountHolderName;

    @NotBlank(message = "UPI ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$",
        message = "UPI ID must be in a valid format (e.g. user@okbank)"
    )
    private String upiId;

    @NotNull(message = "Current balance is required")
    @DecimalMin(value = "0.0", message = "Current balance must be positive")
    private BigDecimal currentBalance;

    @NotNull(message = "Account status is required")
    private AccountStatus accountStatus;
}
