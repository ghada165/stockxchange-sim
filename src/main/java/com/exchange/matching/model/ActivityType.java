package com.exchange.matching.model;

/** Types d'evenements enregistres dans le journal d'activite global. */
public enum ActivityType {
    USER_REGISTERED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    PROFILE_UPDATED,
    ORDER_PLACED,
    ORDER_CANCELLED,
    ORDER_REJECTED,
    SYMBOL_CREATED,
    SYMBOL_STATUS_CHANGED,
    SYMBOL_UPDATED,
    SYMBOL_DELETED,
    USER_STATUS_CHANGED,
    MARKET_HOURS_UPDATED
}
