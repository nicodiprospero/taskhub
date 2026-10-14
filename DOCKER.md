# TaskHub em Docker (24/7)

## Pré-requisitos
- Docker Desktop (Windows/Mac) ou Docker Engine (Linux)
- Docker Compose v2 (já vem com Docker Desktop)

## Subir
```bash
docker compose up -d --build
```

App fica em `http://localhost:5000`. O entrypoint roda `seed.py` automaticamente na primeira inicialização (cria Alice + Bob).

## Operação
```bash
docker compose logs -f          # ver logs ao vivo
docker compose ps               # status do container
docker compose restart          # reiniciar
docker compose down             # parar
docker compose down -v          # parar + apagar volumes (zera DB)
```

## Aplicar/reverter patch dentro do container
```bash
docker compose exec taskhub python fix.py
docker compose exec taskhub python unfix.py
docker compose exec taskhub python reset.py
```

## Persistência
- Banco SQLite: volume `taskhub_data` (montado em `/app/data`)
- Uploads de foto: volume `taskhub_uploads` (montado em `/app/app/static/uploads`)
- Estado de segurança (`.security_state`) também em `/app/data`

Rebuild do código preserva os volumes. `docker compose down -v` é o único que zera tudo.

## Restart 24/7
A policy é `restart: unless-stopped`. Se o container crashar, Docker reinicia. Se você reiniciar o sistema operacional, o Docker Engine sobe automaticamente e o container junto.

## Healthcheck
A cada 30s o Docker bate em `/auth/login`. Se falhar 3 vezes, marca como `unhealthy` (e pode ser usado pra alertar/auto-restart com swarm/k8s).

## Hospedagem em produção (deploy real)

Para o app ficar acessível publicamente:

| Provedor | Tier grátis | Como |
|----------|-------------|------|
| **Fly.io** | Sim, ~3 VMs | `fly launch` na pasta, ele detecta o Dockerfile |
| **Railway** | $5/mês de crédito | Conecta repo GitHub, detecta Docker |
| **Render** | Sim (com sleep) | Conecta repo, escolhe Docker |
| **VPS próprio** | Hetzner €3.79/mês | `git pull && docker compose up -d` |

### Exemplo Fly.io (mais rápido)
```bash
# 1. Instalar fly CLI: https://fly.io/docs/hands-on/install-flyctl/
fly auth login
fly launch                     # detecta Dockerfile, cria fly.toml
fly volumes create taskhub_data --size 1
fly deploy
```

### Exemplo VPS
```bash
ssh user@servidor
git clone https://github.com/seu-user/taskhub.git
cd taskhub
docker compose up -d --build
# Configurar Nginx ou Caddy como reverse proxy + HTTPS via Let's Encrypt
```

## Variáveis de ambiente importantes
- `SECRET_KEY` — **trocar em produção** (Flask usa pra assinar sessões)
- `DATA_DIR` — onde escrever o DB (default `/app/data`)
- `SECURITY_STATE_FILE` — caminho do flag (default `/app/data/.security_state`)

## Build sem compose (manual)
```bash
docker build -t taskhub .
docker run -d --name taskhub --restart unless-stopped \
  -p 5000:5000 \
  -v taskhub_data:/app/data \
  -v taskhub_uploads:/app/app/static/uploads \
  -e SECRET_KEY="$(openssl rand -hex 32)" \
  -e DATA_DIR=/app/data \
  -e SECURITY_STATE_FILE=/app/data/.security_state \
  taskhub
```
