# 🏦 Matir Bank

A secure, production-ready, full-stack banking system built with **Java (Spring Boot)** and **Vanilla Web Technologies (HTML, CSS, JS)**. The system is designed with a decentralized security architecture, utilizing a split-key storage design across multiple services, custom data encryption, and integrated fraud detection mechanisms, all optimized to run seamlessly on free-tier hosting environments.

---

## 📌 Project Overview & Goals

- **Primary Goal:** Practical implementation of core **Object-Oriented Programming (OOP)** principles, robust **Data Structures & Algorithms (DSA)**, and production-level system security.
- **Architecture Strategy:** Completely decoupled Frontend and Backend communicating via RESTful API services.
- **Free-Tier Compatibility:** Structured to deploy on Vercel (Frontend) and HuggingFace Spaces/Docker (Backend & Key Service) without requiring external paid database servers.

---

## ⚙️ Tech Stack & Deployment Architecture

| Layer | Technologies & Services | Description |
| :--- | :--- | :--- |
| **Frontend UI** | HTML5, Vanilla CSS3, JavaScript (ES6+, No Frameworks) | Decoupled client interface. |
| **Hosting (Frontend)** | [Vercel](https://vercel.com/) | Static content delivery network hosting. |
| **Backend Core** | Java 17 + Spring Boot | REST APIs, Security Context, Transaction Management. |
| **Hosting (Backend)** | [HuggingFace Spaces](https://huggingface.co/spaces) (Docker) | Containerized hosting of spring-boot applications. |
| **Database** | SQLite | Embedded file-based relational databases (`data.db`, `keys.db`). |
| **Security & Auth** | JWT (JSON Web Tokens), Spring Security, BCrypt | Token-based stateless authentication & password hashing. |
| **Encryption** | XOR-based custom encryption | Transparent cell-level encryption for sensitive database fields. |

---

## 📐 System Architecture

```mermaid
graph TD
    User([User Browser]) -->|HTTPS| Frontend[Vercel Frontend]
    Frontend -->|REST API Requests| Backend[Space 2: Spring Boot Backend]
    
    subgraph HuggingFace Space 2 (Main System)
        Backend -->|Reads / Writes| MainDB[(SQLite: data.db)]
    end
    
    subgraph HuggingFace Space 1 (Security Service)
        KeyService[Space 1: Key Service API] -->|Reads / Writes| KeyDB[(SQLite: keys.db)]
    end
    
    Backend -->|Internal REST Calls + X-Internal-Secret| KeyService
```

- **Space 2 (Main Backend):** Exposes endpoints for client authentication, deposits, withdrawals, transfers, and administrative actions. Stores encrypted customer details and transactional data in `data.db`.
- **Space 1 (Key Service):** Maintains a separate database (`keys.db`) mapped by record IDs to their respective encryption keys.
- **Decoupled Architecture:** Communication between backend services and the frontend is fully stateless. Role checks are executed strictly server-side using Spring Security `@PreAuthorize`.

---

## 🔒 Security Design & Fraud Prevention

### 1. Split-Storage Key System
For every encrypted entity created in `data.db`, a corresponding random key is generated and stored in `keys.db` (Space 1). When reading/writing data, the backend queries the Key Service.
- **Security Barrier:** Compromising the main database alone reveals only ciphertext. Compromising the Key Service alone yields keys without data context.
- **Service Authentication:** Communication between Space 1 and Space 2 is secured using a shared token validated through the custom `X-Internal-Secret` header.

### 2. Password Protection
All user passwords undergo one-way hashing with **BCrypt** before storage. Plan text passwords are never stored or logged.

### 3. Suspicious Activity Lockout (Fraud Detection)
The system actively monitors transactional patterns to safeguard accounts:
- **Velocity Limit:** Maximum of **3 withdrawals or transfers** within a **5-minute window**. Exceeding this freezes the account.
- **Single Transaction Limit:** Transactions exceeding **100,000 BDT** in a single operation automatically lock the account.
- **Lockout State:** Once frozen, the account blocks all transactional activities until verified by administrative staff.

### 4. Manager Credential Obfuscation
The initial bootstrap manager credentials are built into the codebase using a Base64 encoded XOR masking strategy at compile-time to prevent plaintext exposure in source files:
- **Email:** `251-15-596@diu.edu.bd`
- **Password:** `Ceo-of-MatirBank$251-15-596`

---

## 📋 Database Schema

### Main Database (`data.db` - Space 2)

```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,              -- XOR Encrypted
    email TEXT UNIQUE,      -- XOR Encrypted (lower-case indexable)
    password TEXT,          -- BCrypt Hashed
    role TEXT CHECK(role IN ('CUSTOMER', 'ADMIN', 'MANAGER')),
    created_at DATETIME
);

CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_number TEXT UNIQUE,   -- Plaintext lookup index
    user_id INTEGER,              -- Plaintext relation key
    balance TEXT,                 -- XOR Encrypted
    account_type TEXT CHECK(account_type IN ('SAVINGS', 'CURRENT')),
    is_active BOOLEAN,
    is_approved BOOLEAN DEFAULT FALSE,
    is_frozen BOOLEAN DEFAULT FALSE,
    approved_by TEXT,
    created_at DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_account TEXT,
    to_account TEXT,
    amount TEXT,                  -- XOR Encrypted
    type TEXT CHECK(type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER')),
    description TEXT,             -- XOR Encrypted
    timestamp DATETIME
);

CREATE TABLE loans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    principal TEXT,                -- XOR Encrypted
    interest_rate REAL,
    tenure_months INTEGER,
    status TEXT CHECK(status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
    created_at DATETIME,
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);
```

### Key Database (`keys.db` - Space 1)

```sql
CREATE TABLE keys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_type TEXT, -- e.g., 'user', 'account', 'transaction', 'loan'
    record_id TEXT,   -- Plaintext target identifier (like ID or account number)
    xor_key TEXT      -- Randomly generated UUID-based XOR key
);
```

---

## ⚡ Data Structures & Algorithms Used

| Component / Utility | DSA Application | Rationale |
| :--- | :--- | :--- |
| **Transaction History** | `Stack` (LIFO) | Fetches and displays recent operations first. |
| **Loan Approvals** | `Queue` (FIFO) | Ensures a fair, chronological approval queue (First-Applied, First-Processed). |
| **Account Lookups** | `HashMap` | $O(1)$ constant time lookups mapped via cached identifiers. |
| **Historical Range Filtering** | `Binary Search` | Fast $O(\log n)$ range query detection on sorted transaction logs. |
| **Cell Encryption** | `XOR Cipher` | Lightweight symmetric $O(n)$ data masking suitable for cell-level records. |
| **Installment Projections** | **EMI Formula** | Standard compound amortization: $EMI = P \times \frac{r(1+r)^n}{(1+r)^n - 1}$ |

---

## 👥 Roles & Permission Hierarchy

The platform implements three hierarchical profiles: `MANAGER` $\rightarrow$ `ADMIN` $\rightarrow$ `CUSTOMER`.

| Operation | Customer | Admin | Manager |
| :--- | :---: | :---: | :---: |
| Access Own Dashboard & Balance | ✅ | ✅ | ✅ |
| Initiate Deposit / Withdrawal / Transfer | ✅ | ✅ | ✅ |
| View Personal Transactions | ✅ | ✅ | ✅ |
| View System-wide Transactions | ❌ | ✅ | ✅ |
| Request New Loans | ✅ | ❌ | ❌ |
| Verify & Approve/Reject Loans | ❌ | ✅ | ✅ |
| Open / Block Customer Accounts | ❌ | ✅ | ✅ |
| Audit All Bank Customers | ❌ | ✅ | ✅ |
| Provision / Revoke Admin Privileges | ❌ | ❌ | ✅ |
| Adjust Global Interest Rates & Customer Caps | ❌ | ❌ | ✅ |

---

## 🛠️ Local Development & Running the Project

The project provides a unified startup script `start.sh` to run the frontend, backend, and key service locally.

### Prerequisites
- Bash environment (Linux/macOS)
- Python 3 (used to run the static frontend dev server)
- Java 17 and Maven (configured automatically inside `.tools/` directory if using the wrapper)

### Startup Instructions

Simply run the starter script from the project root:

```bash
chmod +x start.sh
./start.sh
```

This script will:
1. Terminate any orphaned java or python processes on development ports.
2. Initialize and run **Space 1 (Key Service)** on `http://localhost:8081`.
3. Wait for Space 1 to respond, then start **Space 2 (Backend)** on `http://localhost:8080`.
4. Serve the **Frontend UI** via Python's HTTP server on `http://localhost:3000`.

---

## 📂 Project Directory Structure

```
matir-bank/
├── space1-keyservice/         # Encryption Key Management Server
│   ├── Dockerfile             # Container configuration for Key Service (Port 8081)
│   ├── pom.xml
│   └── src/                   # Key Controller, Database access, Entity mappings
│
├── space2-backend/            # Main Business Logic API Server
│   ├── Dockerfile             # Container configuration for Core Backend (Port 7860/8080)
│   ├── pom.xml
│   └── src/                   # Auth, Account, Transaction, Loan, Admin modules
│
├── frontend/                  # Static user interface (HTML/CSS/JS)
│   ├── admin/                 # Admin Login portal
│   ├── manager/               # Manager Login portal
│   ├── css/                   # Stylesheets for layouts and dashboards
│   ├── js/                    # Javascript configuration and handlers
│   ├── dashboard-customer.html
│   ├── dashboard-admin.html
│   ├── dashboard-manager.html
│   └── index.html             # Customer signup / login landing page
│
└── start.sh                   # Dev environments local bootstrap script
```
