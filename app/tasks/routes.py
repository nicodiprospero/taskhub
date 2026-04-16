from flask import render_template, redirect, url_for, flash, request, make_response
from flask_login import login_required, current_user
from app import db
from app.tasks import tasks_bp
from app.tasks.forms import TaskForm
from app.models import Task


@tasks_bp.route('/')
@login_required
def index():
    search = request.args.get('search', '').strip()
    view_mode = request.cookies.get('view_mode', 'list')

    query = Task.query.filter_by(user_id=current_user.id)
    if search:
        query = query.filter(Task.title.ilike(f'%{search}%'))
    tasks = query.order_by(Task.created_at.desc()).all()

    return render_template('tasks/index.html',
                           tasks=tasks,
                           search=search,
                           view_mode=view_mode,
                           title='Minhas Tarefas')


@tasks_bp.route('/tasks/create', methods=['GET', 'POST'])
@login_required
def create():
    form = TaskForm()
    if form.validate_on_submit():
        task = Task(
            title=form.title.data,
            description=form.description.data,
            status=form.status.data,
            priority=form.priority.data,
            user_id=current_user.id
        )
        db.session.add(task)
        db.session.commit()
        flash('Tarefa criada com sucesso!', 'success')
        return redirect(url_for('tasks.index'))
    return render_template('tasks/create.html', form=form, title='Nova Tarefa')


@tasks_bp.route('/tasks/edit/<int:task_id>', methods=['GET', 'POST'])
@login_required
def edit(task_id):
    # ============================================================
    # VULNERABILIDADE INTENCIONAL: IDOR (Insecure Direct Object Reference)
    # Também conhecida como Broken Access Control — OWASP Top 10 #1 (2021).
    #
    # O código abaixo carrega a tarefa apenas pelo ID, SEM verificar se ela
    # pertence ao usuário autenticado. Qualquer usuário logado pode editar
    # tarefas de QUALQUER outro usuário apenas alterando o ID na URL.
    #
    # EXPLOIT DE DEMONSTRAÇÃO:
    #   1. Alice (user_id=1) cria tarefa "Senha do WiFi: 12345" -> task_id=3
    #   2. Bob (user_id=2) faz login, cria qualquer tarefa para descobrir o
    #      padrão da URL: /tasks/edit/4
    #   3. Bob troca o ID manualmente: /tasks/edit/3
    #   4. Bob vê e pode alterar/apagar a tarefa privada da Alice!
    #
    # CORREÇÃO (aplicar ao vivo após a demo):
    #   task = Task.query.filter_by(
    #       id=task_id, user_id=current_user.id
    #   ).first_or_404()
    # ============================================================
    task = Task.query.get_or_404(task_id)  # <-- VULNERÁVEL: sem checagem de dono
    form = TaskForm(obj=task)
    if form.validate_on_submit():
        task.title = form.title.data
        task.description = form.description.data
        task.status = form.status.data
        task.priority = form.priority.data
        db.session.commit()
        flash('Tarefa atualizada com sucesso!', 'success')
        return redirect(url_for('tasks.index'))
    return render_template('tasks/edit.html', form=form, task=task, title='Editar Tarefa')


@tasks_bp.route('/tasks/delete/<int:task_id>', methods=['POST'])
@login_required
def delete(task_id):
    # VULNERÁVEL: mesma falha de IDOR na rota de exclusão.
    # Correção: filter_by(id=task_id, user_id=current_user.id).first_or_404()
    task = Task.query.get_or_404(task_id)
    db.session.delete(task)
    db.session.commit()
    flash('Tarefa excluída com sucesso.', 'info')
    return redirect(url_for('tasks.index'))


@tasks_bp.route('/tasks/set_view/<mode>')
@login_required
def set_view(mode):
    if mode not in ('list', 'kanban'):
        mode = 'list'
    response = make_response(redirect(url_for('tasks.index')))
    response.set_cookie('view_mode', mode, max_age=60 * 60 * 24 * 30)
    return response
