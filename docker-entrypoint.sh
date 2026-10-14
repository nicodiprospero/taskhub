#!/usr/bin/env bash
set -e

DATA_DIR="${DATA_DIR:-/app/data}"
STATE_FILE="${SECURITY_STATE_FILE:-$DATA_DIR/.security_state}"

mkdir -p "$DATA_DIR"
mkdir -p /app/app/static/uploads

# Se nao existe banco, popula com Alice e Bob
if [ ! -f "$DATA_DIR/taskhub.db" ]; then
    echo "[entrypoint] Banco nao encontrado em $DATA_DIR. Rodando seed..."
    python seed.py || echo "[entrypoint] Aviso: seed falhou, app vai criar tabelas vazias"
fi

# Garante que o estado de seguranca existe
if [ ! -s "$STATE_FILE" ]; then
    echo "vulnerable" > "$STATE_FILE"
fi

echo "[entrypoint] DATA_DIR=$DATA_DIR"
echo "[entrypoint] Estado: $(cat "$STATE_FILE")"
echo "[entrypoint] Iniciando: $@"

exec "$@"
