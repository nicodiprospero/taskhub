# Relatório — TaskHub
## CP-2 / Coding for Security — Desenvolvimento de Aplicação Web com Flask

**Turma:** 1TDCPA-2025
**Disciplina:** Coding for Security
**Semestre:** 2º/2025
**Data de entrega:** _____________

### Integrantes do grupo

| Nome completo | RM |
|---------------|----|
|               |    |
|               |    |
|               |    |
|               |    |

---

## 1. Resumo do projeto

**TaskHub** é uma aplicação web de gestão de tarefas pessoais, desenvolvida em Flask, com potencial de se tornar um produto SaaS real. O usuário pode se cadastrar, criar tarefas com título, descrição, prioridade e status, buscar por palavra-chave, e alternar entre duas visualizações (lista ou Kanban) — com a preferência persistida em cookie.

O sistema foi construído seguindo boas práticas de organização (Application Factory, Blueprints), utilizando três Extensions do ecossistema Flask, banco de dados relacional SQLite com CRUD completo e autenticação baseada em sessão. Como parte do requisito de segurança, foi inserida intencionalmente uma vulnerabilidade de **IDOR (Insecure Direct Object Reference)**, demonstrada e corrigida durante a apresentação.

---

## 2. Tema e motivação

O tema escolhido foi **gestão de tarefas pessoais em formato SaaS**. Essa categoria de software é familiar ao público em geral (Trello, Todoist, Notion, Asana), representa um serviço de tecnologia real e permite evoluir de um MVP acadêmico para um produto de mercado, caso o grupo quisesse dar continuidade.

O foco não foi construir um clone de Trello com todas as funcionalidades, mas entregar um núcleo funcional, bem organizado e seguro, que demonstrasse claramente cada requisito exigido pelo enunciado.

---

## 3. Arquitetura e organização do código

### 3.1 Estrutura de diretórios

```
taskhub/
├── app/
│   ├── __init__.py         # Application Factory
│   ├── models.py           # Models User e Task (SQLAlchemy)
│   ├── auth/               # Blueprint de autenticação
│   │   ├── __init__.py     # Registro do Blueprint
│   │   ├── forms.py        # LoginForm, RegisterForm (Flask-WTF)
│   │   └── routes.py       # /auth/register, /auth/login, /auth/logout
│   ├── tasks/              # Blueprint de tarefas
│   │   ├── __init__.py
│   │   ├── forms.py        # TaskForm
│   │   └── routes.py       # /, /tasks/create, /tasks/edit, /tasks/delete, /tasks/set_view
│   ├── templates/          # HTML (base, auth, tasks)
│   └── static/style.css    # Estilo customizado, sem frameworks externos
├── config.py               # SECRET_KEY, URI do banco
├── run.py                  # Ponto de entrada
├── requirements.txt
└── taskhub.db              # SQLite (gerado automaticamente)
```

### 3.2 Padrão Application Factory

Usamos a função `create_app()` em `app/__init__.py` para instanciar a aplicação. Esse padrão permite:

- Criar múltiplas instâncias (útil para testes).
- Inicializar as extensions de forma controlada.
- Registrar os Blueprints de forma modular.

### 3.3 Blueprints

> **Pontuação: 1,0 ponto (critério 5)**

Cada domínio da aplicação foi isolado em um Blueprint próprio:

| Blueprint | Prefixo | Responsabilidade |
|-----------|---------|------------------|
| `auth_bp` | `/auth` | Cadastro, login, logout |
| `tasks_bp` | `/` | Listagem, CRUD de tarefas, cookie de visualização |

**Por que usamos:** mantém o código organizado por contexto, evita o "God file" (um único `app.py` gigante) e deixa claro onde cada funcionalidade mora. Facilita crescimento futuro — se quiséssemos adicionar um módulo de cobrança, criaríamos um `billing_bp` sem mexer no que já existe.

### 3.4 Flask Extensions

> **Pontuação: 1,0 ponto (critério 6)**

Usamos três extensions do ecossistema Flask:

| Extension | Uso na aplicação |
|-----------|------------------|
| **Flask-SQLAlchemy** | ORM: mapeia as classes `User` e `Task` para tabelas SQLite. Gera queries parametrizadas automaticamente (protege contra SQL Injection). |
| **Flask-Login** | Controle de sessão: `login_user()`, `logout_user()`, decorator `@login_required` para proteger rotas, e `current_user` para acessar o usuário logado. |
| **Flask-WTF** | Formulários com validação: `LoginForm`, `RegisterForm`, `TaskForm`. Gera proteção CSRF automática em todos os POSTs. |

---

## 4. Banco de Dados

> **Pontuação: 1,0 ponto (critério 7)**

Banco: **SQLite** (`taskhub.db`), criado automaticamente na primeira execução via `db.create_all()`.

### 4.1 Tabelas

**Tabela `user`**

| Coluna | Tipo | Restrições |
|--------|------|------------|
| id | Integer | PK, auto-increment |
| username | String(64) | Unique, NOT NULL |
| email | String(120) | Unique, NOT NULL |
| password_hash | String(256) | NOT NULL (hash via `werkzeug.security`) |

**Tabela `task`**

| Coluna | Tipo | Restrições |
|--------|------|------------|
| id | Integer | PK, auto-increment |
| title | String(140) | NOT NULL |
| description | Text | |
| status | String(20) | default='pending' (valores: pending, in_progress, done) |
| priority | String(10) | default='medium' (valores: low, medium, high) |
| created_at | DateTime | default=utcnow |
| user_id | Integer | FK → user.id, NOT NULL |

**Relacionamento:** um usuário tem muitas tarefas (`User.tasks` → lista de `Task`, com `cascade='all, delete-orphan'`).

### 4.2 CRUD completo

| Operação | Rota | Função SQL equivalente |
|----------|------|------------------------|
| **Create** | `POST /tasks/create` | `INSERT INTO task ...` |
| **Read** | `GET /` | `SELECT * FROM task WHERE user_id = ?` |
| **Update** | `POST /tasks/edit/<id>` | `UPDATE task SET ... WHERE id = ?` |
| **Delete** | `POST /tasks/delete/<id>` | `DELETE FROM task WHERE id = ?` |

Todas implementadas via SQLAlchemy ORM, que gera as queries parametrizadas internamente.

---

## 5. Autenticação

> **Pontuação: parte de 1,0 (critério 4)**

- Cadastro via `POST /auth/register` com validação:
  - username entre 3 e 64 caracteres e único;
  - e-mail no formato correto e único;
  - senha com no mínimo 6 caracteres e confirmação.
- Login via `POST /auth/login` com checagem de senha usando `check_password_hash()`.
- Senhas armazenadas **sempre como hash** (nunca em texto puro).
- Logout via `GET /auth/logout`, que encerra a sessão do Flask-Login.
- Todas as rotas de `/tasks/*` e `/` usam `@login_required` — usuário não autenticado é redirecionado para `/auth/login`.

---

## 6. Uso de Cookies

A visualização preferida do usuário (lista ou Kanban) é salva como cookie no navegador, com duração de 30 dias.

**Implementação em `app/tasks/routes.py`:**

```python
@tasks_bp.route('/tasks/set_view/<mode>')
@login_required
def set_view(mode):
    if mode not in ('list', 'kanban'):
        mode = 'list'
    response = make_response(redirect(url_for('tasks.index')))
    response.set_cookie('view_mode', mode, max_age=60 * 60 * 24 * 30)
    return response
```

**Leitura do cookie na rota index:**

```python
view_mode = request.cookies.get('view_mode', 'list')
```

**Por que cookie e não sessão?** A preferência de UI não é dado sensível, não precisa expirar no logout e não consome recurso do servidor. Cookie é a escolha natural e leve para esse caso.

---

## 7. Rotas HTTP (GET e POST)

| Rota | Método | Função |
|------|--------|--------|
| `/auth/register` | GET, POST | Exibe e processa o cadastro |
| `/auth/login` | GET, POST | Exibe e processa o login |
| `/auth/logout` | GET | Encerra sessão |
| `/` | GET | Lista tarefas (com filtro de busca opcional) |
| `/tasks/create` | GET, POST | Exibe e processa criação |
| `/tasks/edit/<id>` | GET, POST | Exibe e processa edição |
| `/tasks/delete/<id>` | POST | Exclui tarefa |
| `/tasks/set_view/<mode>` | GET | Define cookie de visualização |

Todos os formulários usam POST + redirect após sucesso (padrão PRG — *Post/Redirect/Get*), evitando reenvio no refresh.

---

## 8. Usabilidade

> **Pontuação: 1,0 ponto (critério 8)**

- **Navbar fixa** no topo com nome do usuário e atalhos (Nova Tarefa, Sair).
- **Flash messages** coloridas por categoria (sucesso, erro, aviso, info) com botão de fechar.
- **Formulários** com labels, placeholders e mensagens de erro campo a campo.
- **Cartões de tarefa** com borda lateral colorida por prioridade (vermelho = alta, amarelo = média, verde = baixa).
- **Badges** de status (Pendente, Em Progresso, Concluída) com paleta acessível.
- **Toggle** entre lista e Kanban, com estado preservado entre sessões.
- **Confirmação** antes de excluir tarefa.
- **Design responsivo** — layout se adapta em telas estreitas (smartphones/tablets).

---

## 9. Vulnerabilidade — IDOR (Insecure Direct Object Reference)

> **Pontuação: 1,0 ponto (critério 2) + potencial +1,0 (critério 9 — vuln diferente)**

### 9.1 O que é IDOR

IDOR, também chamado de **Broken Access Control**, é a vulnerabilidade **nº 1 do OWASP Top 10 (2021)**. Acontece quando o servidor usa um identificador direto do objeto (ID na URL, por exemplo) para carregar um recurso, mas **não verifica se o usuário autenticado tem permissão de acessar aquele recurso específico**.

O resultado: basta um atacante trocar um número na URL para acessar dados de outros usuários.

Casos reais de empresas que sofreram com IDOR:
- **Facebook** — permitiu apagar fotos de outros usuários (bug bounty de US$ 12.500).
- **Uber** — expôs dados de viagens entre contas.
- **Shopify** — vários IDORs reportados via HackerOne.
- **Instagram** — acesso a mensagens privadas por manipulação de ID.

### 9.2 Onde estava a vulnerabilidade no TaskHub

**Arquivo:** `app/tasks/routes.py`
**Rota afetada:** `GET/POST /tasks/edit/<task_id>` e `POST /tasks/delete/<task_id>`

Código vulnerável:

```python
@tasks_bp.route('/tasks/edit/<int:task_id>', methods=['GET', 'POST'])
@login_required
def edit(task_id):
    task = Task.query.get_or_404(task_id)   # <-- FALHA AQUI
    ...
```

O `@login_required` garante que o usuário **está autenticado**. Mas o `get_or_404` busca a tarefa **só pelo id**, sem checar se ela pertence ao usuário logado. Isso é falha de **autorização** — o usuário autenticado não é o dono daquele recurso, mas o servidor entrega mesmo assim.

### 9.3 Demonstração do ataque

**Cenário:**

1. Alice cria conta e adiciona tarefa confidencial: *"Senha do servidor: admin@2024"* — o ID gerado é `1`.
2. Bob cria uma conta qualquer, faz login, cria uma tarefa dele só para descobrir o formato da URL: `/tasks/edit/4`.
3. Bob troca o ID na URL para `/tasks/edit/1`.
4. O servidor retorna a tarefa da Alice — Bob pode **ler e modificar** o conteúdo.

*(Inserir aqui print da tela do Bob abrindo a tarefa da Alice)*

### 9.4 Impacto

- **Confidencialidade:** Bob acessa dados privados de qualquer outro usuário.
- **Integridade:** Bob pode alterar tarefas que não são dele.
- **Disponibilidade:** Bob pode excluir tarefas de qualquer um.
- **Escalabilidade do ataque:** o ID é sequencial, então um atacante pode escrever um script que percorra `/tasks/edit/1`, `/2`, `/3`... e colete os dados de **todos** os usuários do sistema.

### 9.5 Correção aplicada

**Trecho corrigido em `app/tasks/routes.py`:**

```python
@tasks_bp.route('/tasks/edit/<int:task_id>', methods=['GET', 'POST'])
@login_required
def edit(task_id):
    task = Task.query.filter_by(
        id=task_id,
        user_id=current_user.id
    ).first_or_404()
    ...
```

E a mesma correção na rota `delete`.

**Por que funciona:** agora o SQLAlchemy gera `SELECT * FROM task WHERE id = ? AND user_id = ?`. Se o ID existir mas pertencer a outro usuário, a consulta não retorna nada e o Flask responde **404 Not Found**. O banco passa a ser a fonte de verdade sobre "quem pode ver o quê", e não mais o frontend.

### 9.6 Boas práticas gerais para evitar IDOR

- Sempre fazer **checagem de ownership** (dono do recurso) junto com a autenticação.
- Preferir **UUIDs aleatórios** (não sequenciais) como ID público quando possível, dificultando enumeração.
- Centralizar lógica de autorização em um decorator ou função auxiliar, para evitar esquecer em alguma rota.
- Ter testes automatizados que tentem acessar recursos de outros usuários e esperam 404/403.

---

## 10. Screenshots da aplicação

*(Substituir cada placeholder pelos prints reais antes de entregar)*

1. **Tela de cadastro** — `/auth/register`
   *(inserir print)*
2. **Tela de login** — `/auth/login`
   *(inserir print)*
3. **Lista de tarefas (modo lista)** — `/`
   *(inserir print mostrando tarefas com badges e bordas coloridas)*
4. **Visualização Kanban** — `/` após trocar para Kanban
   *(inserir print com as três colunas)*
5. **DevTools → Cookies** — mostrando `view_mode=kanban` salvo
   *(inserir print)*
6. **Formulário de criação de tarefa** — `/tasks/create`
   *(inserir print)*
7. **Formulário de edição** — `/tasks/edit/<id>`
   *(inserir print)*
8. **Flash message** de sucesso (após criar tarefa)
   *(inserir print)*
9. **Demo da vulnerabilidade** — Bob logado, vendo `/tasks/edit/1` da Alice
   *(inserir print)*
10. **Após a correção** — mesma URL retornando 404
    *(inserir print)*

---

## 11. Como executar o projeto

```bash
# 1. Clonar / extrair o projeto
cd taskhub

# 2. (Opcional, recomendado) criar e ativar venv
python -m venv venv
source venv/bin/activate        # Linux/Mac
venv\Scripts\activate           # Windows

# 3. Instalar dependências
pip install -r requirements.txt

# 4. Rodar
python run.py
```

Acessar em `http://127.0.0.1:5000`.

O banco `taskhub.db` é criado automaticamente na primeira execução.

---

## 12. Checklist de requisitos (autoavaliação)

### Requisitos obrigatórios

- [x] **Tema** — serviço de tecnologia real (gestão de tarefas SaaS)
- [x] **Autenticação** — login funcional, área protegida
- [x] **Cookies** — uso real (preferência de visualização)
- [x] **SQLite + CRUD completo** — User + Task, INSERT/SELECT/UPDATE/DELETE
- [x] **Rotas GET e POST** — formulários e redirecionamentos
- [x] **Blueprints** — 2 (`auth_bp`, `tasks_bp`)
- [x] **Flask Extensions** — 3 (Flask-Login, Flask-WTF, Flask-SQLAlchemy)
- [x] **Usabilidade** — nav, flash, forms validados, design responsivo
- [x] **Vulnerabilidade** — IDOR demonstrado e corrigido

### Pontuação extra

- [x] **Vulnerabilidade diferente (+1,0)** — IDOR é OWASP Top 1 mas raramente aparece em trabalhos acadêmicos (que geralmente usam SQLi ou XSS)
- [x] **Potencial SaaS (+1,0)** — TaskHub é diretamente aplicável como produto real (concorrente simplificado de Todoist/Trello)

---

## 13. Conclusão

Entregamos uma aplicação Flask completa, modular e segura (depois da correção 🙂). O projeto reforçou conceitos de:

- Separação de responsabilidades via Blueprints.
- Uso de ORM para prevenir SQL Injection automaticamente.
- Diferença entre **autenticação** (quem é você) e **autorização** (o que você pode fazer) — este foi o ponto central da vulnerabilidade IDOR.
- Importância de nunca confiar em IDs vindos do cliente sem uma checagem de ownership no backend.

Acima de tudo, aprendemos que **segurança não é só sobre bloquear hackers que tentam injetar código estranho — é também sobre lógica de autorização correta**, que é onde a maioria das vulnerabilidades reais aparece em 2024/2025.
