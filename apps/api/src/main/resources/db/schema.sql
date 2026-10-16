CREATE TABLE IF NOT EXISTS profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    phone TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS addresses (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES profiles(id),
    label TEXT NOT NULL,
    address_line TEXT NOT NULL,
    city TEXT NOT NULL,
    state TEXT NOT NULL,
    pin_code TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1))
);
CREATE TABLE IF NOT EXISTS products (
    sku TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    detail TEXT NOT NULL,
    price REAL NOT NULL CHECK (price > 0),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    art TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS cart_items (
    user_id TEXT NOT NULL REFERENCES profiles(id),
    sku TEXT NOT NULL REFERENCES products(sku),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    PRIMARY KEY (user_id, sku)
);
CREATE TABLE IF NOT EXISTS orders (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES profiles(id),
    retry_key TEXT NOT NULL,
    address_id TEXT NOT NULL REFERENCES addresses(id),
    customer_name TEXT NOT NULL,
    customer_email TEXT NOT NULL,
    customer_phone TEXT NOT NULL,
    address_line TEXT NOT NULL,
    city TEXT NOT NULL,
    state TEXT NOT NULL,
    pin_code TEXT NOT NULL,
    subtotal REAL NOT NULL,
    handling REAL NOT NULL,
    total REAL NOT NULL,
    currency TEXT NOT NULL CHECK (currency = 'INR'),
    status TEXT NOT NULL CHECK (status IN ('pending','confirmed','failed','expired','payment_issue')),
    stock_held INTEGER NOT NULL CHECK (stock_held IN (0,1)),
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (user_id, retry_key)
);
CREATE INDEX IF NOT EXISTS pending_orders_by_user ON orders(user_id) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS orders_by_user ON orders(user_id, created_at);
CREATE TABLE IF NOT EXISTS order_items (
    order_id TEXT NOT NULL REFERENCES orders(id),
    sku TEXT NOT NULL REFERENCES products(sku),
    name TEXT NOT NULL,
    art TEXT NOT NULL,
    unit_price REAL NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total REAL NOT NULL,
    PRIMARY KEY (order_id, sku)
);
CREATE TABLE IF NOT EXISTS payment_intents (
    id TEXT PRIMARY KEY,
    order_id TEXT NOT NULL UNIQUE REFERENCES orders(id),
    expected_amount REAL NOT NULL,
    currency TEXT NOT NULL CHECK (currency = 'INR')
);
CREATE TABLE IF NOT EXISTS payment_attempts (
    event_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES profiles(id),
    order_id TEXT NOT NULL UNIQUE REFERENCES orders(id),
    retry_key TEXT NOT NULL,
    outcome TEXT NOT NULL CHECK (outcome IN ('success','failure','pending','duplicate')),
    payload TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE (user_id, retry_key)
);
CREATE TABLE IF NOT EXISTS payment_attempt_keys (
    user_id TEXT NOT NULL REFERENCES profiles(id),
    retry_key TEXT NOT NULL,
    event_id TEXT NOT NULL REFERENCES payment_attempts(event_id),
    PRIMARY KEY (user_id, retry_key)
);
CREATE TABLE IF NOT EXISTS webhook_events (
    event_id TEXT PRIMARY KEY,
    order_id TEXT NOT NULL REFERENCES orders(id),
    payload_hash TEXT NOT NULL,
    result TEXT NOT NULL,
    received_at TEXT NOT NULL
);
PRAGMA user_version = 4;
