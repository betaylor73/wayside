# GENISYS Serial Communication Protocol

## 5.1 GENISYS SERIAL COMMUNICATION PROTOCOL

### 5.1.1 General Message Format

The GENISYS protocol is a binary, byte oriented, serial, polling protocol in which all messages are framed by unique header/control and terminator bytes. It is normally transmitted and received by asynchronous serial communication controllers configured to process 8 bit characters with one start bit and one stop bit.

A typical GENISYS message is composed of:

- Header / Control character  
- Station address  
- Data bytes (optional)  
- Two checksum bytes (CRC‑16)  
- Terminator character  

Character values preceded by `$` are hexadecimal.

---

### 5.1.1.1 Control Character

There are 13 possible header/control characters in the GENISYS protocol (`$F1`–`$FE`).

- `$F0` — Escape character  
- `$F6` — Message terminator  
- `$FF` — Not used  

Headers `$F1`–`$F3` are slave‑to‑master messages.  
Headers `$F9`–`$FE` are master‑to‑slave messages.

To preserve uniqueness of control characters, data bytes in the range `$F0`–`$FF` are escaped:

- `$F0` followed by `(byte − $F0)`

When `$F0` is received, it is added to the following byte to recover the original value.

---

### 5.1.1.2 Station Address

- Master → Slave: address of the target slave  
- Slave → Master: address of the responding slave  

Valid addresses: `$01`–`$FF`  
Broadcast address: `$00` (master → slaves)

---

### 5.1.1.3 Data Bytes

All data bytes are transmitted as address/data pairs:

- Byte address: `$00`–`$DF` (typically `$00`–`$1F`)  
- Byte data: `$00`–`$FF`  

Bits 1–8 are in the first byte, 9–16 in the second, etc.

---

### 5.1.1.4 Security Checksum

All messages **except**:

- Slave acknowledge (`$F1`)  
- Master non‑secure poll  

include a CRC‑16 checksum using polynomial:

```
X¹⁶ + X¹⁵ + X² + 1
```

The CRC is calculated before inserting escape characters and excludes the terminator.

---

## 5.1.2 Master → Slave Messages

### 5.1.2.1 Common Control Message (`$F9`)

Broadcasts identical data to all slaves.  
Requires common control mode enabled via configuration byte `$E0`.  
Slaves **do not respond**.

---

### 5.1.2.2 Acknowledge and Poll Message (`$FA`)

Acknowledges data received from a slave.

Valid responses:
- `$F1` Acknowledge  
- `$F2` Data  

Unacknowledged data is retransmitted by the slave.

---

### 5.1.2.3 Poll Message (`$FB`)

Requests changed data from a slave.

Formats:
- Secure (CRC‑16)  
- Non‑secure  

Valid responses:
- `$F1` Acknowledge  
- `$F2` Data  

---

### 5.1.2.4 Control Data Message (`$FC`)

Delivers control data to the slave.

- With checkback enabled → only `$F3` valid  
- Without checkback → `$F1` or `$F2`

---

### 5.1.2.5 Indication Recall Message (`$FD`)

Requests the slave’s **entire indication database**.

**Only valid response:**  
- Full indication data message (`$F2`)

---

### 5.1.2.6 Control Execute Message (`$FE`)

Executes previously delivered control data.

Requirements:
- Checkback enabled  
- Must immediately follow valid control data  

Valid responses:
- `$F1` Acknowledge  
- `$F2` Data  

---

## 5.1.3 Slave → Master Messages

### 5.1.3.1 Acknowledge (`$F1`)

Default response when no data is available.  
Non‑secure (no CRC).

---

### 5.1.3.2 Indication Data (`$F2`)

Delivers new or changed data in response to:
- Acknowledge
- Poll
- Control
- Recall
- Control execute

---

### 5.1.3.3 Control Checkback (`$F3`)

Echoes received control data when checkback mode is enabled.  
Executed only if followed by a control execute message.

---

## 5.1.4 Master Driver Operation

- Sends recall to all slaves on startup  
- Waits for response with timeout  
- Validates header, address, CRC, terminator, length  
- Marks slave active after successful recall  
- Retries failed slaves up to 3 times  
- Failed slaves are polled only with recall messages  

---

## 5.1.5 Slave Driver Operation

- Listens continuously  
- Never transmits unless addressed  
- Sets `SERIAL.MASTER.ON` upon valid communication  
- Clears after 5 minutes without communication  

Dual‑port indicators:
- `SER1.MASTER.ON`
- `SER2.MASTER.ON`

---

## 5.1.6 Configuration Control Bytes

Addresses `$E0`–`$FF` reserved.  
Currently only `$E0` used.

Bit definitions:
- 0 — Database complete  
- 1 — Use checkback  
- 2 — Secure poll only  
- 3 — Enable common control  
- 4–7 — Reserved  

---

## 5.1.7 Master → Slave Message Formats

### Common Control (`$F9`)

| Byte | Description |
|---|---|
| 1 | `$F9` |
| 2 | Address |
| n‑2 | CRC low |
| n‑1 | CRC high |
| n | `$F6` |

### Acknowledge & Poll (`$FA`)

| Byte | Description |
|---|---|
| 1 | `$FA` |
| 2 | Address |
| 3–4 | CRC |
| 5 | `$F6` |

### Poll (`$FB`)

Secure:
- Header, address, CRC, terminator

Non‑secure:
- Header, address, terminator

### Control (`$FC`)

Header, address, control byte pairs, CRC, terminator

### Recall (`$FD`) / Execute (`$FE`)

Header, address, CRC, terminator

---

## 5.1.8 Slave → Master Message Formats

### Acknowledge (`$F1`)

Header, address, terminator

### Indication Data (`$F2`)

Header, address, indication byte pairs, CRC, terminator

### Control Checkback (`$F3`)

Header, address, control byte pairs, CRC, terminator

---

## 5.1.9 Control Character Summary

| Character | Function |
|---|---|
| `$F0` | Escape |
| `$F1` | Acknowledge |
| `$F2` | Indication data |
| `$F3` | Control checkback |
| `$F6` | Terminator |
| `$F9` | Common control |
| `$FA` | Ack & poll |
| `$FB` | Poll |
| `$FC` | Control |
| `$FD` | Recall |
| `$FE` | Execute |
