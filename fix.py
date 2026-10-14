"""
Aplica TODAS as correcoes de seguranca ao TaskHub.

Uso: python fix.py

Toggla o estado da aplicacao para PATCHED. A partir desse momento,
qualquer tentativa de exploit via URL leva para a pagina de
"Vulnerabilidade resolvida" ao inves de funcionar.

Vulnerabilidades corrigidas por este script:
  1. IDOR - Edicao/Exclusao de tarefa (/tasks/edit/<id>, /tasks/delete/<id>)
  2. IDOR - Visualizacao de perfil (/profile/<id>)
  3. IDOR - Edicao de perfil (/profile/<id>/edit)
  4. Bypass 2FA + IDOR em troca de senha (/profile/<id>/change-password)
  5. Upload inseguro de arquivos (extensao + sanitizacao)

Use python unfix.py para reativar as vulnerabilidades.
"""
from app.security import patch, VULNS

patch()

print("=" * 60)
print(" PATCH DE SEGURANCA APLICADO")
print("=" * 60)
print()
print("Vulnerabilidades corrigidas:")
print()
for key, info in VULNS.items():
    print(f"  [OK] {info['name']}")
    print(f"       Rota:  {info['route']}")
    print(f"       OWASP: {info['owasp']}")
    print(f"       Fix:   {info['fix']}")
    print()
print("=" * 60)
print(" Tente novamente o exploit no navegador para ver a pagina")
print(" de 'Vulnerabilidade resolvida'.")
print()
print(" Para reativar (rodar a demo de novo): python unfix.py")
print("=" * 60)
