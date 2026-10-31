.PHONY: up down logs test backup restore
up:
	docker compose up --build -d --wait
down:
	docker compose down
logs:
	docker compose logs -f --tail=100
test:
	cd backend && mvn test
	cd frontend && npm ci && npm run test -- --run && npm run build
	cd document-worker && python -m pytest
backup:
	./scripts/backup.sh
restore:
	./scripts/restore.sh "$(ARCHIVE)"
