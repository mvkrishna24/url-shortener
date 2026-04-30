.PHONY: up down build test logs clean restart

## Start PostgreSQL + Redis in detached mode
up:
	docker compose up -d

## Stop containers (keeps volumes)
down:
	docker compose down

## Restart containers
restart:
	docker compose down && docker compose up -d

## Build fat jar, skip tests
build:
	mvn clean package -DskipTests

## Run all tests (Testcontainers will spin up its own DB/Redis)
test:
	mvn test

## Tail logs for all services
logs:
	docker compose logs -f

## Full reset: stop containers AND delete named volumes (destroys data)
clean:
	docker compose down -v
	mvn clean
