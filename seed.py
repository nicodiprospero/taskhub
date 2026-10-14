"""
Script de seed para demonstração.

Uso: python seed.py

Apaga o banco atual, recria as tabelas e popula com 2 usuários de demo
(Alice e Bob) com tarefas pré-definidas, tags e datas variadas.
"""
from datetime import date, datetime, timedelta
from app import create_app, db
from app.models import User, Task, Tag

app = create_app()

with app.app_context():
    db.drop_all()
    db.create_all()
    print("Banco de dados resetado.\n")

    alice = User(username='alice', email='alice@taskhub.com',
                 display_name='Alice Silva', twofa_enabled=True,
                 bio='Engenheira de software · DevOps · café preto')
    alice.set_password('123456')
    bob = User(username='bob', email='bob@taskhub.com',
               display_name='Bob Souza',
               bio='Estudante de cibersegurança 🔓')
    bob.set_password('123456')
    db.session.add_all([alice, bob])
    db.session.commit()

    # Tags da Alice
    tag_trabalho = Tag(name='trabalho', color='coral', user_id=alice.id)
    tag_pessoal = Tag(name='pessoal', color='blue', user_id=alice.id)
    tag_urgente = Tag(name='urgente', color='red', user_id=alice.id)
    tag_estudo = Tag(name='estudo', color='green', user_id=alice.id)
    db.session.add_all([tag_trabalho, tag_pessoal, tag_urgente, tag_estudo])

    # Tags do Bob
    tag_faculdade = Tag(name='faculdade', color='purple', user_id=bob.id)
    tag_casa = Tag(name='casa', color='amber', user_id=bob.id)
    db.session.add_all([tag_faculdade, tag_casa])
    db.session.commit()

    today = date.today()

    alice_tasks = [
        Task(
            title='Senha do servidor de producao',
            description='admin@Senha2024! - NAO COMPARTILHAR\n\nServidor: prod-db-01\nPorta: 5432',
            status='pending', priority='high', user_id=alice.id,
            due_date=today + timedelta(days=3),
            tags=[tag_trabalho, tag_urgente],
        ),
        Task(
            title='Revisar relatorio financeiro Q4',
            description='Receita: R$ 1.2M.\nEnviar revisao ate sexta para o time financeiro.\nIncluir analise comparativa com Q3.',
            status='in_progress', priority='high', user_id=alice.id,
            due_date=today + timedelta(days=2),
            tags=[tag_trabalho],
        ),
        Task(
            title='Comprar presente aniversario Joao',
            description='Sugestao: livro de programacao Python ou jogo de tabuleiro',
            status='pending', priority='low', user_id=alice.id,
            due_date=today + timedelta(days=10),
            tags=[tag_pessoal],
        ),
        Task(
            title='Estudar Flask Blueprints',
            description='Capitulo 7 do livro Flask Web Development',
            status='done', priority='medium', user_id=alice.id,
            due_date=today - timedelta(days=2),
            completed_at=datetime.utcnow() - timedelta(days=1),
            tags=[tag_estudo],
        ),
        Task(
            title='Renovar plano de saude',
            description='Vencimento em poucos dias - prioridade alta',
            status='pending', priority='high', user_id=alice.id,
            due_date=today - timedelta(days=1),  # ATRASADA - mostra o badge
            tags=[tag_pessoal, tag_urgente],
        ),
    ]

    bob_tasks = [
        Task(
            title='Terminar projeto faculdade',
            description='CP2 de Coding for Security\n- Flask\n- SQLite\n- Vulnerabilidade IDOR',
            status='in_progress', priority='medium', user_id=bob.id,
            due_date=today + timedelta(days=5),
            tags=[tag_faculdade],
        ),
        Task(
            title='Lavar o carro',
            description='',
            status='pending', priority='low', user_id=bob.id,
            tags=[tag_casa],
        ),
    ]

    db.session.add_all(alice_tasks + bob_tasks)
    db.session.commit()

    print("=" * 60)
    print("USUARIOS CRIADOS")
    print("=" * 60)
    print(f"Alice -> alice@taskhub.com / 123456 (id={alice.id})")
    print(f"Bob   -> bob@taskhub.com   / 123456 (id={bob.id})")
    print()
    print("=" * 60)
    print("TAREFAS DA ALICE (vitima)")
    print("=" * 60)
    for t in alice_tasks:
        marker = '[ATRASADA] ' if (t.due_date and t.due_date < today and t.status != 'done') else ''
        print(f"  id={t.id:<3} {marker}{t.title}")
    print()
    print("=" * 60)
    print("TAREFAS DO BOB (atacante)")
    print("=" * 60)
    for t in bob_tasks:
        print(f"  id={t.id:<3} {t.title}")
    print()
    print("=" * 60)
    print("DEMO IDOR")
    print("=" * 60)
    print(f"  1. Login como Bob (123456)")
    print(f"  2. Edite uma tarefa propria -> /tasks/edit/<id>")
    print(f"  3. Troque manualmente para /tasks/edit/{alice_tasks[0].id}")
    print(f"  4. Voce vera a tarefa secreta da Alice -> ATAQUE FUNCIONOU!")
    print()
