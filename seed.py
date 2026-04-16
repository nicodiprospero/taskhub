"""
Script de seed para demonstração.

Uso: python seed.py

Apaga o banco atual, recria as tabelas e popula com 2 usuários de demo
(Alice e Bob) com tarefas pré-definidas. Imprime os IDs das tarefas no
final para você decorar antes da apresentação.
"""
import os
from app import create_app, db
from app.models import User, Task

DB_FILE = os.path.join(os.path.dirname(__file__), 'taskhub.db')

app = create_app()

with app.app_context():
    # 1. Limpa banco existente
    db.drop_all()
    db.create_all()
    print("Banco de dados resetado.\n")

    # 2. Cria usuarios
    alice = User(username='alice', email='alice@taskhub.com')
    alice.set_password('123456')

    bob = User(username='bob', email='bob@taskhub.com')
    bob.set_password('123456')

    db.session.add_all([alice, bob])
    db.session.commit()

    # 3. Cria tarefas da Alice (a vitima da demo IDOR)
    alice_tasks = [
        Task(
            title='Senha do servidor de producao',
            description='admin@Senha2024! - NAO COMPARTILHAR',
            status='pending', priority='high', user_id=alice.id
        ),
        Task(
            title='Revisar relatorio financeiro Q4',
            description='Receita: R$ 1.2M. Enviar ate sexta.',
            status='in_progress', priority='high', user_id=alice.id
        ),
        Task(
            title='Comprar presente aniversario Joao',
            description='Sugestao: livro de programacao',
            status='pending', priority='low', user_id=alice.id
        ),
    ]

    # 4. Cria tarefas do Bob (o atacante)
    bob_tasks = [
        Task(
            title='Terminar projeto faculdade',
            description='CP2 de Coding for Security',
            status='in_progress', priority='medium', user_id=bob.id
        ),
        Task(
            title='Lavar o carro',
            description='',
            status='pending', priority='low', user_id=bob.id
        ),
    ]

    db.session.add_all(alice_tasks + bob_tasks)
    db.session.commit()

    # 5. Imprime IDs para a demo
    print("=" * 60)
    print("USUARIOS CRIADOS")
    print("=" * 60)
    print(f"Alice -> email: alice@taskhub.com | senha: 123456 | id={alice.id}")
    print(f"Bob   -> email: bob@taskhub.com   | senha: 123456 | id={bob.id}")
    print()
    print("=" * 60)
    print("TAREFAS DA ALICE (vitima)")
    print("=" * 60)
    for t in alice_tasks:
        print(f"  id={t.id:<3} [{t.priority:<6}] {t.title}")
    print()
    print("=" * 60)
    print("TAREFAS DO BOB (atacante)")
    print("=" * 60)
    for t in bob_tasks:
        print(f"  id={t.id:<3} [{t.priority:<6}] {t.title}")
    print()
    print("=" * 60)
    print("DICA PARA A DEMO IDOR:")
    print("=" * 60)
    print(f"  1. Faca login como Bob (senha 123456)")
    print(f"  2. Crie qualquer tarefa -> veja o id dela na URL de edit")
    print(f"  3. Troque manualmente para /tasks/edit/{alice_tasks[0].id}")
    print(f"  4. Voce vera a tarefa secreta da Alice -> DEMO FUNCIONOU!")
    print()
