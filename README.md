# TaskHub

Aplicação web Flask para gestão de tarefas, desenvolvida como projeto da disciplina **Coding for Security** (CP-2 / 1TDCPA-2025).

## Stack
- Flask 3.0 + Application Factory
- SQLite via Flask-SQLAlchemy
- Flask-Login (autenticação)
- Flask-WTF (forms + CSRF)
- 2 Blueprints: `auth` e `tasks`

## Funcionalidades
- Registro, login e logout
- CRUD completo de tarefas (título, descrição, prioridade, status)
- Busca por título
- Duas visualizações (lista e Kanban) com preferência salva em cookie
- Design responsivo, sem frameworks CSS externos

## Como rodar

```bash
pip install -r requirements.txt
python seed.py     # (opcional) cria banco com dados de demo
python run.py
```

Acessar em `http://127.0.0.1:5000`.

## Vulnerabilidade intencional

Este projeto contém uma vulnerabilidade **IDOR (Insecure Direct Object Reference)** inserida propositalmente em `app/tasks/routes.py` nas rotas `/tasks/edit/<id>` e `/tasks/delete/<id>`.

A falha permite que um usuário autenticado acesse e modifique tarefas de outros usuários apenas alterando o ID na URL. A correção (filtro por `user_id=current_user.id`) está documentada nos comentários do código e é aplicada ao vivo durante a apresentação.

**Ver detalhes completos em:** [`RELATORIO.md`](RELATORIO.md) e [`APRESENTACAO.md`](APRESENTACAO.md).

> Aviso: este sistema tem vulnerabilidade educacional — **não usar em produção**.

## Documentação do projeto
- [`RELATORIO.md`](RELATORIO.md) — Relatório técnico para entrega
- [`APRESENTACAO.md`](APRESENTACAO.md) — Roteiro das falas (4 integrantes)
- [`COMO_RODAR.txt`](COMO_RODAR.txt) — Guia rápido para o dia da apresentação
