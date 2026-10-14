"""
Estado de segurança da aplicação (flag toggleável em runtime).

Controla se as vulnerabilidades intencionais (IDOR, bypass 2FA, upload
inseguro) estão ATIVAS ou já FORAM CORRIGIDAS, sem precisar editar o
código durante a apresentação. Basta executar:

    python fix.py    -> aplica todas as correções
    python unfix.py  -> volta ao modo vulnerável (para repetir a demo)

O estado é persistido em um arquivo `.security_state` na raiz do projeto.
"""
import os
from pathlib import Path

# Permite override via env var (uso em Docker com volume montado)
_state_path = os.environ.get('SECURITY_STATE_FILE')
if _state_path:
    STATE_FILE = Path(_state_path)
else:
    STATE_FILE = Path(__file__).resolve().parent.parent / '.security_state'


def is_patched() -> bool:
    """True se as vulnerabilidades já foram corrigidas."""
    if not STATE_FILE.exists():
        return False
    return STATE_FILE.read_text(encoding='utf-8').strip().lower() == 'patched'


def patch():
    STATE_FILE.write_text('patched', encoding='utf-8')


def unpatch():
    STATE_FILE.write_text('vulnerable', encoding='utf-8')


def state_label() -> str:
    return 'CORRIGIDO' if is_patched() else 'VULNERÁVEL'


# Lista de vulnerabilidades documentadas (usado em telas e logs)
VULNS = {
    'idor_task_edit': {
        'name': 'IDOR — Edição/Exclusão de Tarefa',
        'route': '/tasks/edit/<id>',
        'owasp': 'A01:2021 (Broken Access Control)',
        'fix': 'Filtrar Task.query por user_id=current_user.id',
    },
    'idor_profile_view': {
        'name': 'IDOR — Visualização de Perfil',
        'route': '/profile/<id>',
        'owasp': 'A01:2021 (Broken Access Control)',
        'fix': 'Bloquear acesso quando user_id != current_user.id',
    },
    'idor_profile_edit': {
        'name': 'IDOR — Edição de Perfil',
        'route': '/profile/<id>/edit',
        'owasp': 'A01:2021 (Broken Access Control)',
        'fix': 'Validar dono do recurso antes de aceitar mudanças',
    },
    'insecure_upload': {
        'name': 'Upload Inseguro de Arquivo',
        'route': '/profile/<id>/edit',
        'owasp': 'A04:2021 (Insecure Design) + A05:2021 (Misconfig)',
        'fix': 'Sanitizar filename + validar extensão + limitar tamanho',
    },
    'twofa_bypass': {
        'name': 'Bypass de 2FA + IDOR em troca de senha',
        'route': '/profile/<id>/change-password?bypass_2fa=...',
        'owasp': 'A07:2021 (Identification & Authentication Failures)',
        'fix': 'Remover backdoor de URL + exigir 2FA server-side',
    },
}
