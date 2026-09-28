.PHONY: up down db logs test package clean

up:
	docker compose up --build

down:
	docker compose down

db:
	docker compose up -d db

logs:
	docker compose logs -f app

test:
	docker run --rm -v "$(CURDIR):/workspace" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B verify

package:
	docker run --rm -v "$(CURDIR):/workspace" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B package

clean:
	docker run --rm -v "$(CURDIR):/workspace" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B clean

