package com.meshpay.transactionservice.entity;

public enum TransactionStatus {
    CREATED,
    ENCRYPTED,
    ROUTING,
    DELIVERED,
    SUCCESS,
    FAILED,
    EXPIRED
}
