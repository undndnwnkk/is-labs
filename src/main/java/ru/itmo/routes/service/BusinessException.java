package ru.itmo.routes.service;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class BusinessException extends IllegalArgumentException {
    public BusinessException(String message) { super(message); }
}
