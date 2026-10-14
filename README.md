# TaskHub

Aplicação web em Flask para gerenciamento de tarefas, feita como projeto de **Coding for Security**.

O projeto tem duas partes: uma aplicação funcional de tarefas e uma demo controlada de vulnerabilidades de controle de acesso. As falhas existem de propósito para apresentação e não devem ser usadas em produção.

## Stack

- Flask 3
- SQLite
- Flask-SQLAlchemy
- Flask-Login
- Flask-WTF
- CSRFProtect
- Blueprints para separar módulos

## Funcionalidades

- Cadastro, login e logout
- CRUD de tarefas
- Tags por usuário
- Filtros e busca
- Visualização em lista e Kanban
- Preferência de visualização salva em cookie
- Tema claro/escuro salvo em cookie
- Perfil com estatísticas
- Upload de foto de perfil
- Bulk delete
- Tarefas atrasadas com destaque visual

## Como rodar

```bash
pip install -r requirements.txt
python reset.py
python start.py
```

Acesse:

```text
http://127.0.0.1:5000
```

## Contas de demo

| Usuário | E-mail | Senha |
|---|---|---|
| Alice | alice@taskhub.com | 123456 |
| Bob | bob@taskhub.com | 123456 |

## Scripts

| Script | O que faz |
|---|---|
| `reset.py` | recria o banco, popula Alice/Bob e deixa o sistema vulnerável |
| `start.py` | inicia o servidor com o roteiro de exploits no terminal |
| `fix.py` | muda o sistema para modo corrigido |
| `unfix.py` | reativa as vulnerabilidades para repetir a demo |
| `seed.py` | popula o banco com dados iniciais |
| `run.py` | inicia o Flask de forma simples |

## Vulnerabilidades intencionais

O modo vulnerável demonstra:

- IDOR para visualizar perfil de outro usuário
- IDOR para editar perfil de outro usuário
- Upload inseguro na edição de perfil
- Bypass de 2FA simulado na troca de senha
- IDOR para editar tarefa privada de outro usuário

Exemplo de fluxo da demo: Bob entra na própria conta e tenta acessar recursos da Alice mudando o ID na URL.

## Correção

A correção é aplicada com:

```bash
python fix.py
```

Depois disso, as mesmas URLs deixam de funcionar e passam a exibir uma página explicando a vulnerabilidade corrigida.

A regra principal é validar ownership no backend. Não basta o usuário estar logado; o recurso também precisa pertencer a ele.

> Projeto educacional. Não usar em produção no modo vulnerável.
