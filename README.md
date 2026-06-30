# MeshPay - Distributed Offline Payment Network

MeshPay simulates a secure, offline UPI-like payment network where transaction packets are generated offline by a user, buffered and routed through peer-to-peer relay nodes (representing simulated Bluetooth mesh relays), and settled when they reach an internet-connected gateway bridging to the bank server.

---

## Architecture Diagram

```
+--------------------------------------------------------+
|                     MeshPay Network                    |
+--------------------------------------------------------+
|                                                        |
|   [ User Service ]  (Port 8081)                        |
|          |                                             |
|          | (Offline REST Broadcast Simulation)         |
|          v                                             |
|   [ Relay Service 1 ] <---> [ Relay Service 2 ]        |
|      (Port 8082)               (Port 8083)             |
|          \                         /                   |
|           \                       / (Propagate Mesh)   |
|            v                     v                     |
|         [ Gateway Service ] (Port 8084)                |
|                  |                                     |
|                  | (Internet Settlement)               |
|                  v                                     |
|           [ Bank Service ]  (Port 8085)                |
|                  |                                     |
|                  v                                     |
|             [ MySQL DB ]                               |
|                                                        |
+--------------------------------------------------------+
```

---

## Folder Structure

```
meshpay/
 ├── common-library/       # Shared DTOs, constants, utilities
 ├── user-service/         # User auth and creation terminal (Port 8081)
 ├── relay-service-1/      # Mesh routing node 1 (Port 8082)
 ├── relay-service-2/      # Mesh routing node 2 (Port 8083)
 ├── gateway-service/      # Internet payment gateway gateway (Port 8084)
 ├── bank-service/         # Banking ledger settlement (Port 8085)
 ├── frontend/             # React + Vite client dashboard (Port 3000)
 ├── mysql-init/           # SQL schemas bootloader
 ├── pom.xml               # Parent aggregator configuration
 ├── docker-compose.yml    # Full stack Docker composer
 └── README.md             # Documentation
```

---

## Technology Stack

### Backend
- **Java 21**
- **Spring Boot 3**
- **Maven**
- **Spring Web**
- **Spring Data JPA**
- **Spring Security (Classpath baseline)**
- **Lombok**
- **MySQL Driver & Validation**

### Frontend
- **React**
- **Vite**
- **Tailwind CSS v3**
- **React Router**
- **Axios**

---

## Port Allocations

| Service | Port | Database |
| :--- | :--- | :--- |
| **Frontend (React)** | `3000` | — |
| **User Service** | `8081` | `meshpay_user` |
| **Relay Service 1** | `8082` | `meshpay_relay_1` |
| **Relay Service 2** | `8083` | `meshpay_relay_2` |
| **Gateway Service** | `8084` | `meshpay_gateway` |
| **Bank Service** | `8085` | `meshpay_bank` |
| **MySQL Database** | `3306` | — |

---

## Setup & Running with Docker Compose

To start the database, all backend microservices, and the frontend server, run:

```bash
# Build and start all services in detached mode
docker compose up --build -d

# View status of running containers
docker compose ps

# View logs for a specific service
docker compose logs -f user-service
```

Once running, access:
- **Frontend Panel**: [http://localhost:3000](http://localhost:3000)
- **Health Verification checks**:
  - User: `http://localhost:8081/health`
  - Relay 1: `http://localhost:8082/health`
  - Relay 2: `http://localhost:8083/health`
  - Gateway: `http://localhost:8084/health`
  - Bank: `http://localhost:8085/health`

---

## Local Development (Without Docker)

1. Ensure a MySQL database is running on `localhost:3306` with username `root` and password `rootpassword`.
2. Build the shared common library first:
   ```bash
   cd common-library
   mvn clean install
   ```
3. Run each backend service independently from its folder:
   ```bash
   mvn spring-boot:run
   ```
4. Run the frontend:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
