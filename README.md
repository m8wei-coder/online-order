# Online Order

A full-stack online food ordering application inspired by DoorDash. Customers can browse restaurants, view menus, add items to a cart, and check out.

- `backend/` — Spring Boot 4 (Java 21) REST API
- `frontend/doordash-app/` — React 18 + Ant Design SPA

## Tech Stack

**Backend**
- Spring Boot 4.0.5, Spring Web MVC, Spring Data JDBC
- Spring Security (form login, session-based, BCrypt-delegating password encoder, JDBC `UserDetailsManager`)
- PostgreSQL 15 (local via Docker Compose, RDS in production)
- Caffeine cache (`expireAfterWrite=60s`) for restaurant/menu lookups
- Gradle build, Java 21 toolchain

**Frontend**
- React 18, Create React App (react-scripts 4)
- Ant Design 4 for UI components
- Dev server proxies `/` → `http://localhost:8080` (see `package.json`)

**Infrastructure**
- Docker (linux/amd64 image built via buildx)
- AWS ECR (image registry), AWS ECS (service hosting), AWS RDS (PostgreSQL)

## Project Structure

```
backend/
├── src/main/java/com/laioffer/onlineorder/
│   ├── OnlineOrderApplication.java   # Spring Boot entry point (@EnableCaching)
│   ├── AppConfig.java                # Security filter chain + JdbcUserDetailsManager
│   ├── DevRunner.java                # Local dev bootstrap
│   ├── controller/                   # CartController, CustomerController, MenuController
│   ├── service/                      # CartService, CustomerService, MenuItemService, RestaurantService
│   ├── repository/                   # Spring Data JDBC repositories
│   ├── entity/                       # Customer, Restaurant, MenuItem, Cart, OrderItem
│   ├── model/                        # DTOs and request bodies (CartDto, RestaurantDto, RegisterBody, ...)
│   └── hello/                        # Sample endpoint
├── src/main/resources/
│   ├── application.yaml              # Datasource, Jackson, cache, logging config
│   ├── database-init.sql             # Schema (runs when INIT_DB=always)
│   └── static/                       # Built frontend assets served by Spring
├── Dockerfile
├── docker-compose.yml                # Local PostgreSQL
└── build.gradle

frontend/doordash-app/
├── src/
│   ├── App.js                        # Layout + auth gating
│   ├── utils.js                      # fetch wrappers for backend APIs
│   └── components/                   # LoginForm, SignupForm, FoodList, MyCart
└── package.json
```

## API

All endpoints are served by the Spring Boot app. Endpoints under `/cart` require an authenticated session.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST   | `/signup`                              | public | Register a new customer (`email`, `password`, `first_name`, `last_name`) |
| POST   | `/login`                               | public | Form login (`username`, `password`) — handled by Spring Security |
| POST   | `/logout`                              | public | Invalidate session |
| GET    | `/restaurants/menu`                    | public | List all restaurants with their menus |
| GET    | `/restaurant/{restaurantId}/menu`      | public | Menu items for a restaurant |
| GET    | `/cart`                                | auth   | Current user's cart |
| POST   | `/cart`                                | auth   | Add a menu item to the cart (`menu_id`) |
| POST   | `/cart/checkout`                       | auth   | Clear cart and complete checkout |

Jackson is configured with `SNAKE_CASE` property naming, so JSON payloads use snake_case keys (e.g. `menu_id`, `first_name`).

## Local Development

### Prerequisites
- Java 21
- Node.js 16+ (CRA 4 needs `NODE_OPTIONS=--openssl-legacy-provider` on newer Node, already wired into the `start` script)
- Docker (for the local Postgres container)

### 1. Start PostgreSQL

```bash
cd backend
docker compose up -d
```

This brings up Postgres 15 on `localhost:5432` with database `onlineorder` and password `secret`.

### 2. Run the backend

In `backend/application.yaml`, switch the datasource URL to the localhost line and the password to `secret` (the RDS values are committed for App Runner). Then:

```bash
cd backend
./gradlew bootRun
```

On first run, set `INIT_DB=always` so `database-init.sql` seeds the schema; flip back to `never` afterwards.

The API listens on `http://localhost:8080`.

### 3. Run the frontend

```bash
cd frontend/doordash-app
npm install
npm start
```

The dev server runs on `http://localhost:3000` and proxies API calls to `http://localhost:8080` via the `proxy` field in `package.json`.

### Tests

```bash
cd backend && ./gradlew test
cd frontend/doordash-app && npm test
```

## Deployment (AWS)

The backend container is published to ECR and served by App Runner; the database lives on RDS PostgreSQL. ECS injects the DB connection settings as environment variables (`DATABASE_URL`, `DATABASE_PORT`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`).

### Build & push the image

```bash
# from backend/
./gradlew build

docker buildx create --use
docker buildx build \
  --platform=linux/amd64 \
  -t <account>.dkr.ecr.<region>.amazonaws.com/onlineorder:latest \
  --load .

aws ecr get-login-password --region <region> \
  | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com

docker push <account>.dkr.ecr.<region>.amazonaws.com/onlineorder:latest
```

### Dockerfile

```dockerfile
FROM --platform=linux/amd64 eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/OnlineOrder-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Required environment variables (ECS)

- `DATABASE_URL` — RDS endpoint hostname
- `DATABASE_PORT` — typically `5432`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `INIT_DB` — `always` for the first deploy to seed the schema, then `never`

### Serving the frontend

Run `npm run build` in `frontend/doordash-app/` and copy `build/` into `backend/src/main/resources/static/` before building the JAR — Spring serves the SPA from the same origin as the API, which avoids CORS and lets the session cookie flow naturally.
