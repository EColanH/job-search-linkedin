# syntax=docker/dockerfile:1
FROM python:3.12-slim
RUN --mount=type=secret,id=proxy_ca,required=false \
    set -eu; set --; \
    if [ -f /run/secrets/proxy_ca ]; then set -- -o Acquire::https::CaInfo=/run/secrets/proxy_ca; fi; \
    apt-get "$@" update; \
    apt-get "$@" install -y --no-install-recommends fonts-dejavu-core; \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt requirements.lock ./
RUN --mount=type=secret,id=proxy_ca,required=false \
    if [ -f /run/secrets/proxy_ca ]; then export PIP_CERT=/run/secrets/proxy_ca; fi; \
    pip install --no-cache-dir -r requirements.txt
COPY app.py .
RUN useradd --uid 10001 --create-home worker && mkdir -p /data && chown worker:worker /data
USER worker
ENV DOCUMENT_STORAGE=/data
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--no-access-log"]
