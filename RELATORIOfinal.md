# Relatório — TaskHub
## CP-2 / Coding for Security — Aplicação Web com Flask

**Turma:** 1TDCPA-2025  
**Disciplina:** Coding for Security  
**Semestre:** 2º/2025  
**Data de entrega:** _____________

### Integrantes do grupo

| Nome completo | RM |
|---|---|
| Nicolas Di Prospero | — |
| | |
| | |
| | |
| | |

---

## 1. Visão geral do projeto

O **TaskHub** é uma aplicação web feita em Flask para organização de tarefas. A ideia foi montar um sistema simples de usar, mas com recursos suficientes para parecer uma aplicação real: cadastro de usuários, login, painel de tarefas, filtros, tags, visualização em lista ou Kanban, tema claro/escuro e página de perfil.

Além da parte funcional, o projeto foi pensado como uma demo de segurança. Algumas vulnerabilidades foram deixadas de propósito para mostrar, na prática, falhas de controle de acesso. Ou seja, elas não foram tratadas como “bugs acidentais”, mas como parte do ambiente controlado da apresentação.

Na demonstração, o sistema começa em modo vulnerável. Depois, rodamos um script de correção e repetimos os mesmos acessos para mostrar a diferença entre uma rota apenas autenticada e uma rota com autorização correta.

---

## 2. Motivação

Escolhemos um gerenciador de tarefas porque é um tipo de sistema fácil de entender durante a apresentação e, ao mesmo tempo, bom para demonstrar autenticação, CRUD, banco de dados e autorização por usuário.

Esse tema combina bem com a parte de segurança porque cada tarefa e cada perfil pertencem a uma conta específica. Assim, dá para mostrar claramente a diferença entre:

- **Autenticação:** confirmar quem está logado.
- **Autorização:** confirmar se o usuário logado pode acessar ou alterar aquele recurso.

A falha principal explorada acontece quando o sistema sabe que o usuário está logado, mas não confere se o recurso acessado realmente pertence a ele.

---

## 3. Arquitetura do sistema

A aplicação foi organizada usando o padrão **Application Factory** do Flask. Isso ajuda a separar a criação da aplicação, o registro dos Blueprints e a configuração das extensões, evitando deixar toda a lógica em um único arquivo.

Estrutura principal:

```text
taskhub/
├── app/
│   ├── __init__.py
│   ├── models.py
│   ├── security.py
│   ├── auth/
│   ├── tasks/
│   ├── templates/
│   └── static/
├── config.py
├── run.py
├── start.py
├── reset.py
├── fix.py
├── unfix.py
├── seed.py
├── requirements.txt
└── taskhub.db
```

### 3.1 Blueprints

O projeto usa Blueprints para separar as responsabilidades principais:

| Blueprint | Responsabilidade |
|---|---|
| `auth` | cadastro, login, logout e rotas de perfil |
| `tasks` | listagem, criação, edição, exclusão, filtros e visualizações |

Essa divisão deixa o código mais organizado e facilita a manutenção, principalmente porque as rotas de autenticação não ficam misturadas com as rotas de tarefas.

### 3.2 Extensões usadas

| Extensão | Uso no projeto |
|---|---|
| Flask-SQLAlchemy | ORM para trabalhar com o SQLite |
| Flask-Login | controle de sessão, login/logout e proteção de rotas |
| Flask-WTF | formulários e validações |
| CSRFProtect | proteção adicional para requisições POST |

---

## 4. Banco de dados

O banco usado é **SQLite**, que atende bem ao objetivo acadêmico do projeto e facilita resetar a demo várias vezes.

As principais entidades são:

### User

Guarda os dados do usuário, como nome, e-mail, hash de senha, bio, foto de perfil e flag de 2FA.

### Task

Representa uma tarefa criada por um usuário. Cada tarefa tem título, descrição, status, prioridade, prazo, data de criação, data de atualização e vínculo com o usuário dono.

### Tag

Permite classificar tarefas por categorias. As tags também pertencem a um usuário.

### task_tags

Tabela intermediária usada para a relação many-to-many entre tarefas e tags.

---

## 5. Funcionalidades implementadas

O TaskHub possui:

- Registro, login e logout.
- Dashboard com contadores de tarefas.
- CRUD completo de tarefas.
- Filtro por status, prioridade, tag e busca por texto.
- Ordenação de tarefas.
- Visualização em lista e Kanban.
- Preferência de visualização salva em cookie.
- Tema claro/escuro salvo em cookie.
- Página de perfil com estatísticas.
- Upload de imagem de perfil.
- Tags coloridas.
- Exclusão em massa de tarefas.
- Indicação visual de tarefas atrasadas.
- Páginas demonstrando vulnerabilidades corrigidas.

---

## 6. Cookies

O sistema usa cookies apenas para preferências de interface, não para dados sensíveis.

| Cookie | Finalidade |
|---|---|
| `view_mode` | guarda se o usuário prefere lista ou Kanban |
| `theme` | guarda o tema claro ou escuro |

Essa escolha faz sentido porque são preferências locais de uso. Mesmo depois de sair da conta, o navegador pode lembrar como o usuário prefere visualizar a aplicação.

---

## 7. Rotas principais

| Rota | Método | Função |
|---|---|---|
| `/auth/register` | GET/POST | cadastro |
| `/auth/login` | GET/POST | login |
| `/auth/logout` | GET | logout |
| `/` | GET | dashboard/listagem de tarefas |
| `/tasks/create` | GET/POST | criação de tarefa |
| `/tasks/edit/<id>` | GET/POST | edição de tarefa |
| `/tasks/delete/<id>` | POST | exclusão de tarefa |
| `/profile/<id>` | GET | visualização de perfil |
| `/profile/<id>/edit` | GET/POST | edição de perfil |
| `/profile/<id>/change-password` | GET/POST | troca de senha |

---

## 8. Vulnerabilidades intencionais

As vulnerabilidades abaixo foram mantidas de propósito para a demonstração da disciplina. Elas fazem parte do roteiro de exploração e correção.

### 8.1 IDOR na visualização de perfil

No modo vulnerável, Bob consegue acessar o perfil de Alice alterando o ID na URL:

```text
/profile/1
```

O problema é que o backend carrega o perfil pelo ID recebido e não valida se aquele perfil pertence ao usuário logado.

### 8.2 IDOR na edição de perfil

Bob também consegue acessar:

```text
/profile/1/edit
```

Com isso, ele pode alterar dados do perfil de Alice. Essa falha mostra que proteger apenas a tela visual não basta; a rota no servidor precisa validar permissão.

### 8.3 Upload inseguro

Na edição de perfil vulnerável, o upload aceita arquivos sem validação forte de extensão e nome. Isso foi incluído para demonstrar outro erro comum em aplicações web: confiar demais no arquivo enviado pelo usuário.

### 8.4 Bypass de 2FA junto com IDOR

A rota de troca de senha simula uma proteção por 2FA. Porém, no modo vulnerável, existe um parâmetro de bypass usado na demo:

```text
/profile/1/change-password?bypass_2fa=admin_override_2024
```

A ideia é mostrar como uma exceção de teste ou de “admin” deixada no código pode anular uma camada inteira de segurança.

### 8.5 IDOR em tarefas

Bob consegue acessar uma tarefa privada de Alice alterando o número da URL:

```text
/tasks/edit/1
```

O impacto é direto: leitura, alteração ou exclusão de dados que pertencem a outra conta.

---

## 9. Correção demonstrada

A correção é aplicada com:

```bash
python fix.py
```

Esse script muda o estado da aplicação para modo corrigido. A partir daí, as rotas vulneráveis passam a validar a propriedade do recurso antes de liberar acesso.

A correção conceitual é sempre a mesma: não basta buscar um registro apenas pelo `id`; também é necessário filtrar pelo usuário dono.

Exemplo:

```python
task = Task.query.filter_by(
    id=task_id,
    user_id=current_user.id
).first_or_404()
```

Assim, mesmo que uma tarefa exista, ela não será retornada se não pertencer ao usuário logado.

---

## 10. Scripts de apoio

| Script | Função |
|---|---|
| `reset.py` | recria o banco, popula Alice/Bob e volta para modo vulnerável |
| `start.py` | inicia o servidor e mostra os exploits disponíveis |
| `fix.py` | aplica o estado corrigido |
| `unfix.py` | reativa as vulnerabilidades para repetir a demo |
| `seed.py` | cria dados iniciais de usuários, tarefas e tags |
| `run.py` | entrada simples da aplicação Flask |

---

## 11. Como executar

```bash
cd taskhub
pip install -r requirements.txt
python reset.py
python start.py
```

Depois, acessar:

```text
http://127.0.0.1:5000
```

Credenciais da demo:

| Usuário | E-mail | Senha | Papel na demo |
|---|---|---|---|
| Alice | alice@taskhub.com | 123456 | vítima |
| Bob | bob@taskhub.com | 123456 | atacante |

---

## 12. Checklist de requisitos

- [x] Aplicação web Flask.
- [x] Autenticação de usuário.
- [x] SQLite.
- [x] CRUD completo.
- [x] Rotas GET e POST.
- [x] Uso de cookies.
- [x] Blueprints.
- [x] Flask extensions.
- [x] Interface utilizável.
- [x] Vulnerabilidades intencionais demonstráveis.
- [x] Correção demonstrável ao vivo.

---

## 13. Conclusão

O TaskHub entrega uma aplicação funcional e também serve como laboratório de segurança. O ponto mais importante do projeto é mostrar que autenticação não resolve tudo: mesmo logado corretamente, um usuário não deve conseguir acessar recursos de outra pessoa.

As vulnerabilidades foram deixadas intencionalmente no modo inicial para fins didáticos. Depois da execução do patch, o sistema passa a bloquear os mesmos acessos indevidos, mostrando a diferença entre uma rota apenas autenticada e uma rota realmente autorizada.
