from datetime import date, datetime
from flask import (render_template, redirect, url_for, flash, request,
                   make_response, abort)
from flask_login import login_required, current_user
from app import db
from app.tasks import tasks_bp
from app.tasks.forms import TaskForm
from app.models import Task, Tag
from app.security import is_patched


# ----- Helpers ---------------------------------------------------------

TAG_PALETTE = ['coral', 'green', 'blue', 'amber', 'purple', 'pink', 'gray']


def _get_or_create_tag(name, user_id):
    name = name.strip().lower()
    if not name:
        return None
    tag = Tag.query.filter_by(name=name, user_id=user_id).first()
    if not tag:
        # cor estável baseada no hash do nome
        color = TAG_PALETTE[hash(name) % len(TAG_PALETTE)]
        tag = Tag(name=name, user_id=user_id, color=color)
        db.session.add(tag)
        db.session.flush()
    return tag


def _apply_tags(task, tags_string):
    task.tags = []
    if not tags_string:
        return
    seen = set()
    for raw in tags_string.split(','):
        name = raw.strip().lower()
        if not name or name in seen:
            continue
        seen.add(name)
        tag = _get_or_create_tag(name, current_user.id)
        if tag:
            task.tags.append(tag)


def _tags_to_string(task):
    return ', '.join(t.name for t in task.tags)


# ----- Routes ----------------------------------------------------------

@tasks_bp.route('/')
@login_required
def index():
    search = request.args.get('search', '').strip()
    view_mode = request.cookies.get('view_mode', 'list')
    status_filter = request.args.get('status', '')
    priority_filter = request.args.get('priority', '')
    tag_filter = request.args.get('tag', '')
    sort = request.args.get('sort', 'created_desc')

    query = Task.query.filter_by(user_id=current_user.id)

    if search:
        query = query.filter(Task.title.ilike(f'%{search}%'))
    if status_filter in ('pending', 'in_progress', 'done'):
        query = query.filter(Task.status == status_filter)
    if priority_filter in ('low', 'medium', 'high'):
        query = query.filter(Task.priority == priority_filter)
    if tag_filter:
        query = query.filter(Task.tags.any(Tag.name == tag_filter.lower()))

    sort_map = {
        'created_desc': Task.created_at.desc(),
        'created_asc': Task.created_at.asc(),
        'due_asc': Task.due_date.asc().nullslast(),
        'due_desc': Task.due_date.desc().nullslast(),
        'priority': db.case((Task.priority == 'high', 1),
                            (Task.priority == 'medium', 2),
                            (Task.priority == 'low', 3)),
        'title': Task.title.asc(),
    }
    query = query.order_by(sort_map.get(sort, Task.created_at.desc()))
    tasks = query.all()

    user_tags = Tag.query.filter_by(user_id=current_user.id).order_by(Tag.name).all()

    return render_template(
        'tasks/index.html',
        tasks=tasks,
        search=search,
        view_mode=view_mode,
        status_filter=status_filter,
        priority_filter=priority_filter,
        tag_filter=tag_filter,
        sort=sort,
        user_tags=user_tags,
        today=date.today(),
        title='Minhas tarefas',
    )


@tasks_bp.route('/tasks/<int:task_id>')
@login_required
def detail(task_id):
    task = Task.query.filter_by(id=task_id, user_id=current_user.id).first_or_404()
    return render_template('tasks/detail.html', task=task,
                           today=date.today(), title=task.title)


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
            due_date=form.due_date.data,
            user_id=current_user.id,
        )
        if form.status.data == 'done':
            task.completed_at = datetime.utcnow()
        db.session.add(task)
        db.session.flush()
        _apply_tags(task, form.tags.data)
        db.session.commit()
        flash('Tarefa criada com sucesso.', 'success')
        return redirect(url_for('tasks.index'))
    return render_template('tasks/create.html', form=form, title='Nova tarefa')


@tasks_bp.route('/tasks/edit/<int:task_id>', methods=['GET', 'POST'])
@login_required
def edit(task_id):
    # ============================================================
    # VULNERABILIDADE INTENCIONAL: IDOR (Broken Access Control)
    # OWASP Top 10 #1 (2021).
    #
    # Carregamos a tarefa só pelo ID, sem verificar dono.
    # Qualquer usuário logado pode editar tarefas de OUTROS usuários
    # apenas trocando o ID na URL.
    #
    # CORREÇÃO (aplicar ao vivo na demo):
    #   task = Task.query.filter_by(
    #       id=task_id, user_id=current_user.id
    #   ).first_or_404()
    # ============================================================
    if is_patched():
        task = Task.query.filter_by(id=task_id, user_id=current_user.id).first()
        if task is None:
            return render_template('security/fixed.html',
                                   vuln_key='idor_task_edit',
                                   attempted_url=request.path,
                                   attempted_id=task_id,
                                   current_id=current_user.id), 403
    else:
        task = Task.query.get_or_404(task_id)  # <-- VULNERÁVEL
    form = TaskForm(obj=task)
    if request.method == 'GET':
        form.tags.data = _tags_to_string(task)
    if form.validate_on_submit():
        task.title = form.title.data
        task.description = form.description.data
        task.mark_status(form.status.data)
        task.priority = form.priority.data
        task.due_date = form.due_date.data
        _apply_tags(task, form.tags.data)
        db.session.commit()
        flash('Tarefa atualizada.', 'success')
        return redirect(url_for('tasks.detail', task_id=task.id))
    return render_template('tasks/edit.html', form=form, task=task,
                           title='Editar tarefa')


@tasks_bp.route('/tasks/delete/<int:task_id>', methods=['POST'])
@login_required
def delete(task_id):
    # Mesma falha de IDOR aqui (proposital).
    if is_patched():
        task = Task.query.filter_by(id=task_id, user_id=current_user.id).first()
        if task is None:
            return render_template('security/fixed.html',
                                   vuln_key='idor_task_edit',
                                   attempted_url=request.path,
                                   attempted_id=task_id,
                                   current_id=current_user.id), 403
    else:
        task = Task.query.get_or_404(task_id)
    db.session.delete(task)
    db.session.commit()
    flash('Tarefa excluída.', 'info')
    return redirect(url_for('tasks.index'))


@tasks_bp.route('/tasks/quick-status/<int:task_id>/<status>', methods=['POST'])
@login_required
def quick_status(task_id, status):
    if status not in ('pending', 'in_progress', 'done'):
        abort(400)
    task = Task.query.filter_by(id=task_id, user_id=current_user.id).first_or_404()
    task.mark_status(status)
    db.session.commit()
    flash(f'Tarefa marcada como "{status}".', 'success')
    return redirect(request.referrer or url_for('tasks.index'))


@tasks_bp.route('/tasks/bulk-delete', methods=['POST'])
@login_required
def bulk_delete():
    ids = request.form.getlist('selected')
    if not ids:
        flash('Nenhuma tarefa selecionada.', 'warning')
        return redirect(url_for('tasks.index'))
    deleted = Task.query.filter(
        Task.id.in_(ids),
        Task.user_id == current_user.id,
    ).delete(synchronize_session=False)
    db.session.commit()
    flash(f'{deleted} tarefa(s) excluída(s).', 'info')
    return redirect(url_for('tasks.index'))


@tasks_bp.route('/tasks/set_view/<mode>')
@login_required
def set_view(mode):
    if mode not in ('list', 'kanban'):
        mode = 'list'
    response = make_response(redirect(request.referrer or url_for('tasks.index')))
    response.set_cookie('view_mode', mode, max_age=60 * 60 * 24 * 30)
    return response


@tasks_bp.route('/set-theme/<theme>')
def set_theme(theme):
    if theme not in ('light', 'dark'):
        theme = 'light'
    response = make_response(redirect(request.referrer or url_for('tasks.index')))
    response.set_cookie('theme', theme, max_age=60 * 60 * 24 * 365)
    return response
