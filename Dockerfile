# =====================================================================
# TaskHub - Dockerfile
# Imagem leve baseada em python:3.11-slim, rodando atras de gunicorn.
# =====================================================================
FROM python:3.11-slim

# Tini para reaping de zombie processes + dumb-init style
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl tini \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt && pip install gunicorn==21.2.0

COPY . .

# Garante que diretorios persistentes existam
RUN mkdir -p /app/app/static/uploads /app/data \
    && touch /app/.security_state

# Entrypoint script
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 5000

# Healthcheck - bate na pagina de login
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
    CMD curl -fsS http://127.0.0.1:5000/auth/login >/dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/docker-entrypoint.sh"]
CMD ["gunicorn", "-w", "2", "-b", "0.0.0.0:5000", "--access-logfile", "-", "--error-logfile", "-", "run:app"]
