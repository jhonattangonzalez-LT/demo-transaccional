package com.quind.paylink.dto;

import com.quind.paylink.model.Account;
import com.quind.paylink.model.AccountType;
import java.math.BigDecimal;

public record BalanceResponse(
        String accountNumber,
        String holderName,
        String bankCode,
        AccountType type,
        BigDecimal balance,
        String currency) {

    public static BalanceResponse from(Account account) {
        return new BalanceResponse(account.getNumber(), account.getHolderName(), account.getBank().getCode(),
                account.getType(), account.getBalance(), "COP");
    }
}
