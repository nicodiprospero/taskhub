"""
Reverte o patch e reativa todas as vulnerabilidades.

Uso: python unfix.py

Util para repetir a demo varias vezes durante a apresentacao.
"""
from app.security import unpatch

unpatch()

print("=" * 60)
print(" PATCH REVERTIDO - VULNERABILIDADES REATIVADAS")
print("=" * 60)
print()
print(" O sistema voltou ao estado VULNERAVEL.")
print(" Os exploits (IDOR, bypass 2FA, upload inseguro) voltam a")
print(" funcionar normalmente.")
print()
print(" Use python fix.py para corrigir tudo de novo.")
print("=" * 60)
