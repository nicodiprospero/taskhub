"""
Reseta o banco de dados do TaskHub e popula com dados de demo.

Uso: python reset.py

- Apaga a tabela inteira e recria com schema atual
- Cria Alice (vitima) e Bob (atacante) com tarefas pre-definidas
- Volta as vulnerabilidades para o modo VULNERAVEL (desfaz fix.py)
- Limpa a pasta de uploads
"""
import shutil
from pathlib import Path
import seed  # executa o seed completo
from app.security import unpatch

UPLOAD_DIR = Path(__file__).resolve().parent / 'app' / 'static' / 'uploads'

if UPLOAD_DIR.exists():
    for f in UPLOAD_DIR.iterdir():
        if f.is_file():
            f.unlink()
    print(f"Pasta de uploads limpa: {UPLOAD_DIR}")

UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
unpatch()
print("\nEstado: VULNERAVEL (vulnerabilidades ativas para a demo).")
print("Reset completo.")
