# yammer-bridge

On-prem bridge that drives fiscal and printing hardware for the yammer backend.

It runs at the venue (next to the devices), connects **outbound** to the backend
over a **raw WebSocket** (the devices are behind NAT and can't accept inbound
connections), receives print jobs, and drives:

- **DATECS DP-25MX** fiscal cash registers — TCP `:3999` — fiscal receipts
- **ESC/POS** thermal printers (e.g. HPRT TP805L) — TCP `:9100` — non-fiscal proforma bills

It replies to each job with the fiscal result (document number, serial, totals, or error).

## Why a bridge?

Cash registers / printers live on the venue LAN with no public address. The bridge
is the only component that can reach them, so the backend hands jobs to it over the
WebSocket the bridge holds open. This mirrors the original servio "cash" app, but the
transport was switched from **STOMP-over-SockJS** to a **raw WebSocket** to match
yammer's stack (`/ws/orders` etc.) — fewer moving parts, no SockJS handshake.

## Run

```bash
mvn spring-boot:run
# or
mvn -q -DskipTests package && java -jar target/bridge-1.0-SNAPSHOT.jar
```

Configure via env vars (see `application.yml`):

| Env var | Default | Meaning |
|---|---|---|
| `BRIDGE_SERVER_URL` | `ws://localhost:8080/ws/bridge` | backend bridge WebSocket URL |
| `BRIDGE_API_KEY` | `change-me` | shared secret (sent as `?key=` + `X-Bridge-Key`) |
| `BRIDGE_RECONNECT_DELAY_SECONDS` | `10` | reconnect delay |
| `PRINTER_PORT` | `3999` | cash-register TCP port (the IP arrives per request) |
| `PRINTER_OPERATOR_CODE` / `PRINTER_OPERATOR_PASS` | `1` / `0001` | fiscal operator |
| `PRINTER_TILL_NUMBER` | `1` | till number |
| `BRIDGE_HTTP_PORT` | `8085` | local HTTP port (manual test endpoints + health) |

## WebSocket protocol

JSON text frames tagged with a `type` field.

**backend → bridge**

```json
{ "type": "RECEIPT", "requestId": "...", "fiscal": true, "paymentMethod": "CARD",
  "cashRegister": "192.168.0.188", "printerIp": "192.168.0.50",
  "lines": [ { "name": "Cola", "quantity": 2, "unitPrice": 12.00, "vat": 21 } ] }
```

`fiscal` selects the device: `true` → fiscal receipt on the DATECS cash register at
`cashRegister`; `false` → non-fiscal receipt on the HPRT thermal printer at `printerIp`.

```json
{ "type": "INFO_RECEIPT", "requestId": "...", "printerIp": "192.168.0.50",
  "table": "M12", "orderNos": [101, 102],
  "lines": [ { "name": "Cola", "quantity": 2, "unitPrice": 12.00, "lineTotal": 24.00 } ],
  "total": 24.00 }
```

**bridge → backend**

```json
{ "type": "RECEIPT_RESULT", "status": "OK", "requestId": "...",
  "receiptNumber": "000123", "fiscalReceiptId": "000123",
  "cashRegisterSerial": "...", "issuedAt": "2026-06-07T12:00:00",
  "totalAmount": 24.00, "paymentMethod": "CARD",
  "errorCode": null, "errorMessage": null }
```

VAT% → DATECS fiscal group is fixed to the device programming: `21→A`, `11→B`, `0→C`
(matches the yammer VAT seed). Payment method → payMode: `CASH→0`, `CARD→1`, `CHECK→2`.

## Manual testing (no backend needed)

```bash
# fiscal receipt → DATECS cash register
curl -X POST http://localhost:8085/print -H 'Content-Type: application/json' -d '{
  "requestId":"t1","fiscal":true,"paymentMethod":"CARD","cashRegister":"192.168.0.188",
  "lines":[{"name":"Cola","quantity":2,"unitPrice":12.00,"vat":21}] }'

# non-fiscal receipt → HPRT thermal printer
curl -X POST http://localhost:8085/print -H 'Content-Type: application/json' -d '{
  "requestId":"t1b","fiscal":false,"paymentMethod":"CASH","printerIp":"192.168.0.50",
  "lines":[{"name":"Cola","quantity":2,"unitPrice":12.00,"vat":21}] }'

# proforma bill → HPRT thermal printer
curl -X POST http://localhost:8085/print/info -H 'Content-Type: application/json' -d '{
  "requestId":"t2","printerIp":"192.168.0.50","table":"M12",
  "lines":[{"name":"Cola","quantity":2,"unitPrice":12.00,"lineTotal":24.00}],"total":24.00 }'
```

## Backend hook (implemented in `api`)

The yammer backend drives the bridge over this endpoint:

- `BridgeWsHandler` + `BridgeAuthHandshakeInterceptor` registered at `/ws/bridge` in
  `WebSocketConfig` — the handshake checks `?key=` / `X-Bridge-Key` against
  `bridge.api-key` (`BRIDGE_API_KEY`; blank accepts any handshake, dev only).
- `BridgeService` sends an `INFO_RECEIPT` for the waiter's *Proforma* button, and a
  fiscal `RECEIPT` after each committed non-protocol payment (`AFTER_COMMIT`), to the
  order point's configured printer / cash-register integration IPs.
- The `RECEIPT_RESULT` frame updates `payment.fiscal_status` / `receipt_number`.

## Layout

```
com.yammer.bridge            app: WS client, queue, print services, DTOs, config
com.yammer.bridge.fiscal     DATECS DP-25MX driver (from servio's printer-fpgate)
```
