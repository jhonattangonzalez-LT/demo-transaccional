package com.quind.paylink.dto;

import com.quind.paylink.model.Bank;

public record BankResponse(String code, String name) {

    public static BankResponse from(Bank bank) {
        return new BankResponse(bank.getCode(), bank.getName());
    }
}
