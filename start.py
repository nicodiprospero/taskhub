"""
Inicia o servidor TaskHub e mostra o roteiro de exploits.

Uso: python start.py
"""
from app import create_app
from app.security import state_label, VULNS

app = create_app()

print("=" * 60)
print(" TASKHUB - servidor de demo")
print("=" * 60)
print(f" Estado: {state_label()}")
print(" URL:    http://127.0.0.1:5000")
print()
print(" Credenciais (apos rodar reset.py):")
print("   Alice (vitima)   alice@taskhub.com / 123456")
print("   Bob   (atacante) bob@taskhub.com   / 123456")
print()
print(" EXPLOITS PARA TESTAR (logado como Bob):")
print("   1. Ver perfil Alice:    /profile/1")
print("   2. Editar perfil Alice: /profile/1/edit")
print("   3. Trocar senha Alice:  /profile/1/change-password?bypass_2fa=admin_override_2024")
print("   4. Editar tarefa Alice: /tasks/edit/1")
print()
print(" Para corrigir tudo:    python fix.py")
print(" Para reativar:         python unfix.py")
print(" Para resetar banco:    python reset.py")
print("=" * 60)

if __name__ == '__main__':
    app.run(debug=True, host='127.0.0.1', port=5000)
