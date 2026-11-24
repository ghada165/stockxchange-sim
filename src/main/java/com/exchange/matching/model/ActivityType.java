package com.exchange.matching.model;

/** Types d'evenements enregistres dans le journal d'activite global. */
public enum ActivityType {
    USER_REGISTERED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    LOGOUT,
    PASSWORD_CHANGED,
    PROFILE_UPDATED,
    ORDER_PLACED,
    ORDER_CANCELLED,
    ORDER_REJECTED,
    SYMBOL_CREATED,
    SYMBOL_STATUS_CHANGED,
    SYMBOL_UPDATED,
    SYMBOL_DELETED,
    USER_STATUS_CHANGED,
    USER_ROLE_CHANGED,
    MARKET_HOURS_UPDATED
}
