# Security Policy - Password Hashing

## Overview
GameMasterX uses adaptive password hashing to protect user credentials.

## Implementation
- Algorithm: BCrypt (adaptive, work factor configurable)
- Library: Spring Security Crypto (`spring-security-crypto`)
- Default work factor: 12 (secure default, can be increased over time)
- Codec: `com.gamemasterx.server.security.PasswordCodec`

## Usage
```java
String hash = PasswordCodec.hash(plaintextPassword);
boolean ok = PasswordCodec.verify(plaintextPassword, storedHash);
```

## Requirements
- Plaintext passwords must never be persisted, logged, or returned in responses.
- Hashing parameters use secure defaults and may be tuned via configuration.
- All credential verification must go through `PasswordCodec.verify`.

## Compliance
- No plaintext passwords in logs, exceptions, or database.
- Adaptive algorithm allows cost increase over time without data migration.
