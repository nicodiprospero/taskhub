# Roteiro de Apresentação — TaskHub

**Duração total estimada:** 10–12 minutos
**Integrantes:** 4 pessoas (Pessoa A, B, C, D — substituir pelos nomes)

---

## Divisão das falas

| Bloco | Responsável | Tempo | O que fala |
|-------|-------------|-------|------------|
| 1. Abertura & Tema | **Pessoa A** | ~1 min | Apresenta o grupo, o problema, a ideia do produto |
| 2. Arquitetura Técnica | **Pessoa B** | ~2,5 min | Stack, Blueprints, Extensions, modelo de dados |
| 3. Demo Funcional | **Pessoa C** | ~3 min | Roda o sistema ao vivo: registrar, logar, CRUD, cookie |
| 4. Vulnerabilidade + Correção | **Pessoa D** | ~3,5 min | Demo do ataque IDOR + correção ao vivo |
| 5. Encerramento | **Todos** | ~1 min | Aprendizados + dúvidas |

---

## Bloco 1 — Abertura (Pessoa A)

> "Boa tarde! Somos o grupo [nomes]. Nosso projeto se chama **TaskHub** — uma plataforma SaaS de gestão de tarefas pessoais, no estilo Trello ou Todoist, porém simplificada.
>
> O problema que buscamos resolver: profissionais e estudantes precisam de uma forma rápida de organizar tarefas com prioridade e status, com visualização em lista **e** em quadro Kanban — e o usuário quer que o sistema **lembre** qual visualização ele prefere entre sessões.
>
> Nosso foco foi entregar um sistema **simples, funcional e bem estruturado**, seguindo as boas práticas de Flask e implementando uma vulnerabilidade real para depois corrigi-la ao vivo."

---

## Bloco 2 — Arquitetura Técnica (Pessoa B)

> "A aplicação segue o padrão **Application Factory** do Flask. Abrimos o arquivo `app/__init__.py`."

**Mostrar em tela:** `app/__init__.py`

> "Aqui criamos a instância do Flask e inicializamos três extensions:
>
> - **Flask-SQLAlchemy** — ORM que conversa com o SQLite, nos protege de SQL Injection por padrão e mapeia tabelas para classes Python.
> - **Flask-Login** — gerencia sessão do usuário, login/logout e o decorator `@login_required` que protege rotas.
> - **Flask-WTF** — cria formulários com validação automática e proteção **CSRF** em todos os POSTs.
>
> Organizamos a aplicação em dois **Blueprints**:"

**Mostrar:** `app/auth/` e `app/tasks/`

> - `auth_bp` (prefixo `/auth`) — registro, login, logout
> - `tasks_bp` — rotas de CRUD e cookie de preferência
>
> "Blueprints nos permitem separar o código por domínio: cada módulo tem suas próprias rotas, formulários e pode ser mantido independente. Se amanhã quisermos virar um produto de verdade, é fácil adicionar um novo Blueprint `billing_bp` ou `api_bp` sem bagunçar o resto."

**Mostrar:** `app/models.py`

> "No banco temos duas tabelas:
>
> - **User** — id, username, email, password_hash (armazenamos **hash**, nunca senha em texto puro, usando `werkzeug.security`).
> - **Task** — id, título, descrição, status, prioridade, data de criação, e `user_id` como chave estrangeira.
>
> O relacionamento um-para-muitos garante que cada tarefa pertence a um usuário. E é aí que, se a gente descuidar, entra a vulnerabilidade que vocês vão ver daqui a pouco."

---

## Bloco 3 — Demonstração Funcional (Pessoa C)

> "Agora vamos ver o sistema funcionando. Estou rodando localmente com `python run.py`."

**Passo a passo na tela (tenha 2 navegadores/abas anônimas abertas):**

1. **Registro**
   > "Crio uma conta nova em `/auth/register`. Os forms do Flask-WTF validam em tempo real: o e-mail precisa ser válido, senha mínima de 6 caracteres, e o sistema verifica se o username ou e-mail já existem."

2. **Login**
   > "Fiz login e fui redirecionado para a tela principal. Já aparece meu nome na navbar."

3. **CRUD — Criar**
   > "Clico em Nova Tarefa, preencho título, descrição, prioridade, status, salvo. Mensagem de flash confirma o sucesso."

4. **CRUD — Listar e Buscar**
   > "A listagem mostra todas as minhas tarefas. Tem barra de busca — digita, filtra pelo título. Tem borda colorida por prioridade: vermelho, amarelo, verde."

5. **Cookie — Troca de visualização** *(destaque este ponto)*
   > "Aqui está o uso de **cookie**. Clico em 'Kanban', mudo para visualização de quadro. Agora olha o detalhe: **se eu fechar o navegador e voltar depois, ele lembra que eu preferia Kanban**."
   >
   > *(Abrir DevTools → Application → Cookies → mostrar `view_mode=kanban`)*
   >
   > "O cookie tem validade de 30 dias. É um caso real de uso: lembrar a preferência de interface do usuário sem precisar gravar isso no banco."

6. **CRUD — Editar e Excluir**
   > "Edito uma tarefa, mudo o status para 'Concluída', salvo. Excluo outra tarefa — pede confirmação antes."

> "Rotas organizadas, GET para exibir forms, POST para salvar, flash messages em cada ação. Tudo protegido por `@login_required` — se eu deslogar e tentar acessar a URL `/`, sou redirecionado pro login."

**[Passa a bola para a Pessoa D]**

> "Agora o **Pessoa D** vai mostrar a parte mais importante: a vulnerabilidade."

---

## Bloco 4 — Vulnerabilidade IDOR + Correção ao Vivo (Pessoa D)

### 4.1 — Explicação teórica (~40s)

> "A vulnerabilidade que escolhemos é **IDOR — Insecure Direct Object Reference**, que faz parte da categoria **Broken Access Control**. Segundo o **OWASP Top 10 de 2021, é a vulnerabilidade nº 1** — a mais comum em aplicações web modernas.
>
> Ela acontece quando o sistema usa um ID direto do objeto na URL (tipo `/tasks/edit/3`) e **confia que o usuário só vai acessar IDs que são dele** — sem verificar no backend se ele realmente é o dono daquele recurso.
>
> Casos reais: Facebook teve um IDOR que permitia apagar qualquer foto, Uber teve um que expunha dados de viagens de outros usuários, e a Shopify pagou bug bounty de milhares de dólares por falhas parecidas."

### 4.2 — Demonstração do ataque (~1 min 30s)

**Setup prévio (antes da apresentação):**
- Deixe **2 abas** abertas em navegadores ou contextos diferentes (uma normal, uma anônima)
- Crie 2 contas: `alice@test.com` e `bob@test.com`
- Faça login como Alice, crie uma tarefa **sensível**: *"Senha do servidor de produção: admin@2024"* — anote o ID que aparece na URL ao clicar em editar (ex: `/tasks/edit/1`)

**Script da demo:**

> "Temos aqui a conta da **Alice**. Ela criou uma tarefa privada, secreta — uma anotação pessoal com uma senha. Ninguém além dela deveria ver isso."

*(Mostra a tarefa da Alice, clica em editar, mostra a URL `/tasks/edit/1`)*

> "Agora vou fazer logout, e fazer login como outro usuário, o **Bob**. Bob não deveria saber absolutamente nada sobre as tarefas da Alice."

*(Login como Bob. Mostra que Bob tem a lista dele, vazia ou com outras tarefas.)*

> "Bob cria uma tarefa qualquer só para descobrir o padrão da URL. Ele clica em 'Editar' na própria tarefa e vê que a URL é `/tasks/edit/4`. A partir daí, ele pensa: 'Será que existe uma tarefa com ID 1? 2? 3?'"

*(Na barra de endereços, troca manualmente de `/tasks/edit/4` para `/tasks/edit/1`)*

> "E aí está — Bob agora vê a tarefa privada da Alice, com a senha dela. Pior: ele **pode editar, pode apagar**. Sem nenhum alerta, sem nenhum aviso, porque o sistema nunca perguntou 'essa tarefa é realmente sua?'"

*(Altera o título da tarefa da Alice para algo engraçado, salva. Mostra que salvou.)*

> "O impacto aqui é quebra total de **isolamento entre contas**. Um atacante consegue enumerar IDs e ler/modificar/apagar dados de qualquer usuário do sistema."

### 4.3 — Correção ao vivo (~1 min)

> "Agora vamos corrigir. Abrindo o arquivo `app/tasks/routes.py`."

**Mostrar linha 69 (aproximadamente):**

```python
task = Task.query.get_or_404(task_id)  # VULNERÁVEL
```

> "O problema está aqui. A gente busca a tarefa **só pelo ID**, sem verificar se pertence ao usuário. A correção é simples: adicionar um filtro exigindo que o `user_id` da tarefa seja o do usuário logado."

**Substituir por:**

```python
task = Task.query.filter_by(
    id=task_id, user_id=current_user.id
).first_or_404()
```

> "E fazemos o mesmo na rota de exclusão."

*(Aplica a mesma mudança na função `delete`. Salva. O Flask em modo debug recarrega automaticamente.)*

> "Agora eu, ainda logado como Bob, tento acessar `/tasks/edit/1` de novo..."

*(Tenta. Recebe 404.)*

> "**404 — Not Found.** Na visão do Bob, aquela tarefa simplesmente não existe. Isolamento restaurado.
>
> Por que isso funciona? Porque agora o SQLAlchemy gera uma query `WHERE id = ? AND user_id = ?`. Se qualquer uma das condições falhar, nada é retornado. O banco vira o responsável por reforçar a regra de negócio, e não só o frontend."

---

## Bloco 5 — Encerramento (Todos)

**Pessoa A:**
> "Para fechar: nós construímos um sistema funcional e bem organizado, com tudo que foi pedido no enunciado."

**Pessoa B (checklist rápido):**
> "Autenticação com Flask-Login, CRUD completo em SQLite, cookies para preferência de visualização, dois Blueprints, três Extensions, rotas GET e POST organizadas."

**Pessoa C:**
> "Usabilidade com forms validados, mensagens de feedback e interface responsiva."

**Pessoa D:**
> "E uma vulnerabilidade real — IDOR, top 1 do OWASP — demonstrada e corrigida ao vivo."

**Todos juntos:**
> "Obrigado! Alguma dúvida?"

---

## Dicas práticas para o dia da apresentação

- [ ] **Ensaie uma vez inteiro.** Os deslizes vão aparecer na primeira passagem.
- [ ] **Tenha o banco limpo antes de começar.** Delete `taskhub.db` e deixe só as 2 contas (Alice, Bob) pré-criadas com dados de demo.
- [ ] **Abra os arquivos que vai mostrar em abas do editor** antes de começar, para não ficar procurando.
- [ ] **Aumente a fonte do terminal e do editor** (Ctrl + +). O fundo da sala vai agradecer.
- [ ] **Tenha Plano B:** se o Flask der erro ao vivo, tenha um **vídeo gravado** de 30s mostrando o ataque funcionando. Nunca confie 100% na internet da sala.
- [ ] **Ninguém lê slide.** Os slides são suporte, o foco é o código e a demo.
- [ ] **Saiba explicar por que** cada escolha foi feita (Blueprint, Extension, IDOR e não outra vuln). O professor pode perguntar.

---

## Perguntas que o professor pode fazer (preparadas)

1. **"Por que escolheram IDOR?"**
   > É OWASP Top 1 em 2021. Representa uma falha de lógica de autorização, diferente de falhas de sintaxe como SQLi ou XSS — então mostra que pensamos em segurança além do óbvio.

2. **"Como vocês saberiam que essa vuln existe em produção?"**
   > Testes de penetração, revisão de código focada em verificar se toda query que carrega recurso por ID também filtra pelo dono, e ferramentas como Burp Suite para interceptar e alterar IDs.

3. **"Por que usar ORM em vez de SQL puro?"**
   > SQLAlchemy parametriza queries automaticamente, prevenindo SQL Injection, e torna o código mais legível e manutenível. SQL puro só faz sentido para queries de alta performance muito específicas.

4. **"Qual a diferença entre Autenticação e Autorização?"**
   > Autenticação = *quem* é você (login). Autorização = *o que* você pode fazer (permissões). IDOR é uma falha de **autorização** — o usuário está autenticado, mas consegue fazer algo que não deveria.

5. **"Por que cookie e não sessão para o modo de visualização?"**
   > Sessão guarda no servidor (memória/banco) e some ao deslogar. Cookie fica no cliente, sobrevive ao logout, não ocupa recurso do servidor — perfeito para preferência simples de UI.
