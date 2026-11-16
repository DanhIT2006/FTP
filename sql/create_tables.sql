-- Tao database truoc: CREATE DATABASE ftpdb;
-- Sau do chay script nay tren database ftpdb

DROP TABLE IF EXISTS transfer_logs;
DROP TABLE IF EXISTS saved_sites;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    home_dir VARCHAR(255) NOT NULL,
    can_read BOOLEAN DEFAULT TRUE,
    can_write BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE transfer_logs (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id),
    action VARCHAR(20) NOT NULL,   -- LOGIN, UPLOAD, DOWNLOAD, DELETE, MKD, RMD, RENAME
    filename VARCHAR(255),
    file_size BIGINT,
    status VARCHAR(20),            -- SUCCESS, FAILED
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE saved_sites (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    host VARCHAR(255) NOT NULL,
    port INT DEFAULT 21,
    username VARCHAR(50),
    password VARCHAR(255)
);
