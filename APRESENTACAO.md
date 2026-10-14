# Roteiro de Apresentação — TaskHub

**Tempo total:** 12 a 15 minutos  
**Grupo:** 4 integrantes

---

## Divisão sugerida

| Parte | Pessoa | Tempo | Conteúdo |
|---|---:|---:|---|
| 1 | A | 1 min | ideia do projeto e objetivo |
| 2 | B | 2–3 min | arquitetura, banco e stack |
| 3 | C | 3 min | demo funcional |
| 4 | D | 5 min | vulnerabilidades e correção |
| 5 | Todos | 1 min | fechamento |

---

## Pessoa A — Abertura

> Boa tarde. Nosso projeto se chama **TaskHub**. Ele é uma aplicação Flask para gerenciamento de tarefas, com login, cadastro, perfil, tags, filtros, visualização em lista e Kanban, além de tema claro e escuro.
>
> A gente escolheu esse tema porque ele parece simples por fora, mas tem um ponto ótimo para cybersegurança: cada tarefa e cada perfil pertencem a um usuário. Então dá para demonstrar bem a diferença entre estar logado e realmente ter permissão para acessar um recurso.
>
> O projeto começa com algumas vulnerabilidades de propósito. Elas fazem parte da demo. Depois, a gente aplica a correção ao vivo e testa os mesmos caminhos de novo.

---

## Pessoa B — Arquitetura e banco

Mostrar rapidamente a estrutura do projeto.

> A aplicação foi organizada com o padrão **Application Factory** do Flask, então a criação do app, registro dos Blueprints e inicialização das extensions ficam separados.
>
> A gente separou o projeto em módulos. A parte de autenticação fica em `auth`, e a parte de tarefas fica em `tasks`. Isso evita um arquivo único gigante e deixa o projeto mais fácil de manter.

### Extensions

> Usamos Flask-SQLAlchemy para o banco, Flask-Login para sessão e controle de login, Flask-WTF para formulários e validação, e CSRFProtect para proteção nos POSTs.

### Banco

> O banco é SQLite. Temos usuários, tarefas, tags e uma tabela de associação entre tarefas e tags. Cada tarefa tem um `user_id`, e esse campo é o ponto central da correção das vulnerabilidades de IDOR.

---

## Pessoa C — Demo funcional

Entrar no sistema normalmente.

1. Abrir `http://127.0.0.1:5000`.
2. Mostrar cadastro ou login.
3. Entrar como Alice.
4. Mostrar dashboard de tarefas.
5. Criar uma tarefa nova.
6. Editar uma tarefa própria.
7. Alternar lista/Kanban.
8. Mostrar cookie `view_mode` no navegador.
9. Alternar tema claro/escuro.
10. Mostrar perfil com estatísticas.

Fala sugerida:

> Antes da parte de segurança, dá para ver que o sistema funciona como uma aplicação normal. O usuário consegue criar, editar, filtrar e visualizar tarefas. Também temos cookies para preferências de interface, como modo de visualização e tema.
>
> Agora a gente vai sair do uso comum e testar o que acontece quando um usuário tenta acessar recursos de outra conta.

---

## Pessoa D — Vulnerabilidades

Rodar antes:

```bash
python reset.py
python start.py
```

Entrar como Bob:

```text
bob@taskhub.com / 123456
```

### Exploit 1 — Ver perfil da Alice

URL:

```text
/profile/1
```

Fala:

> Aqui o Bob está logado na própria conta, mas troca o ID da URL para acessar o perfil da Alice. O problema é que a rota confia no ID recebido e não verifica se o Bob deveria ver esses dados.

### Exploit 2 — Editar perfil da Alice

URL:

```text
/profile/1/edit
```

Fala:

> Agora o impacto é maior. Bob não só vê o perfil, como consegue alterar dados da Alice. Isso mostra uma falha de autorização, não de autenticação. O login existe, mas a permissão por recurso está faltando.

### Exploit 3 — Upload inseguro

Na tela de edição de perfil, mostrar upload.

Fala:

> Também deixamos o upload propositalmente fraco. O sistema vulnerável não valida direito a extensão e o tipo do arquivo. Em produção isso seria perigoso, mas aqui serve para demonstrar por que upload precisa de validação forte.

### Exploit 4 — Troca de senha com bypass de 2FA

URL:

```text
/profile/1/change-password?bypass_2fa=admin_override_2024
```

Fala:

> A Alice tem 2FA ativo. Só que no modo vulnerável existe um bypass simulado, como se fosse uma exceção de admin ou teste esquecida no código. Isso mostra como uma regra especial mal controlada pode quebrar uma proteção inteira.

### Exploit 5 — Editar tarefa privada da Alice

URL:

```text
/tasks/edit/1
```

Fala:

> Esse é o IDOR mais direto. Bob acessa uma tarefa privada da Alice só alterando o número da URL. Como a tarefa tem `user_id`, a correção é filtrar também pelo usuário logado, não só pelo ID da tarefa.

---

## Correção ao vivo

No terminal:

```bash
python fix.py
```

Fala:

> Agora a gente aplicou o patch. Internamente, o sistema mudou para o estado corrigido, e as rotas passam a validar ownership. Ou seja: o recurso precisa existir e também pertencer ao usuário logado.

Testar de novo:

```text
/profile/1
/profile/1/edit
/profile/1/change-password?bypass_2fa=admin_override_2024
/tasks/edit/1
```

Fala:

> Repetindo os mesmos acessos, o sistema não entrega mais os dados. Ele mostra uma página explicando que a vulnerabilidade foi corrigida. O objetivo é deixar claro o antes e depois.

---

## Fechamento

> Em resumo, o TaskHub é uma aplicação funcional, mas também um laboratório de segurança. A principal lição é que `@login_required` não basta. Ele prova quem é o usuário, mas não prova que ele pode acessar qualquer ID da URL.
>
> A correção é sempre validar o dono do recurso no backend. Esse é o ponto central para evitar IDOR e Broken Access Control.

---

## Perguntas prováveis

### Por que IDOR?

Porque é uma falha comum em sistemas reais e fácil de demonstrar: basta trocar um identificador na URL quando o backend não valida ownership.

### O sistema está inseguro de propósito?

Sim. O estado inicial é vulnerável para fins didáticos. Depois da execução de `python fix.py`, as rotas passam a bloquear os mesmos acessos.

### Por que não basta esconder botão no frontend?

Porque o atacante pode chamar a URL diretamente. A autorização precisa estar no backend.

### Qual é a correção principal?

Buscar o recurso pelo ID e pelo dono:

```python
Task.query.filter_by(id=task_id, user_id=current_user.id).first_or_404()
```
