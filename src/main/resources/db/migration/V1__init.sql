CREATE TABLE orders (
    id                  VARCHAR(36)     PRIMARY KEY,
    user_id             VARCHAR(64)     NOT NULL,
    symbol              VARCHAR(16)     NOT NULL,
    side                VARCHAR(8)      NOT NULL,
    type                VARCHAR(8)      NOT NULL,
    price               NUMERIC(19,4),
    quantity            NUMERIC(19,8)   NOT NULL,
    remaining_quantity  NUMERIC(19,8)   NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    sequence            BIGINT          NOT NULL
);

CREATE INDEX idx_orders_symbol_status ON orders (symbol, status);
CREATE INDEX idx_orders_user ON orders (user_id);

CREATE TABLE trades (
    id              VARCHAR(36)     PRIMARY KEY,
    symbol          VARCHAR(16)     NOT NULL,
    buy_order_id    VARCHAR(36)     NOT NULL,
    sell_order_id   VARCHAR(36)     NOT NULL,
    price           NUMERIC(19,4)   NOT NULL,
    quantity        NUMERIC(19,8)   NOT NULL,
    executed_at     TIMESTAMP       NOT NULL
);

CREATE INDEX idx_trades_symbol ON trades (symbol);
CREATE INDEX idx_trades_buy_order ON trades (buy_order_id);
CREATE INDEX idx_trades_sell_order ON trades (sell_order_id);

CREATE TABLE wallets (
    id              VARCHAR(36)     PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL UNIQUE,
    balance         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    locked_balance  NUMERIC(19,4)   NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE positions (
    id              VARCHAR(36)     PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL,
    symbol          VARCHAR(16)     NOT NULL,
    quantity        NUMERIC(19,8)   NOT NULL DEFAULT 0,
    locked_quantity NUMERIC(19,8)   NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    UNIQUE (user_id, symbol)
);
