import os
import random
from pathlib import Path
from flask import (render_template, redirect, url_for, flash, request,
                   session, current_app, abort)
from flask_login import login_user, logout_user, login_required, current_user
from werkzeug.utils import secure_filename
from app import db
from app.auth import auth_bp
from app.auth.forms import (RegisterForm, LoginForm, ChangePasswordForm,
                            EditProfileForm)
from app.models import User
from app.security import is_patched

# Pasta de uploads
UPLOAD_DIR = Path(__file__).resolve().parent.parent / 'static' / 'uploads'
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

# Backdoor de 2FA "deixada por engano" pelo dev (vulnerabilidade intencional)
TWOFA_BACKDOOR = 'admin_override_2024'


def _idor_block(target_user_id, vuln_key):
    """
    Quando as vulnerabilidades estão CORRIGIDAS, bloqueia acesso a outro
    usuário com a página de "Vulnerabilidade resolvida".
    Quando estão VULNERÁVEIS, retorna None (deixa passar).
    """
    if is_patched() and target_user_id != current_user.id:
        return render_template(
            'security/fixed.html',
            vuln_key=vuln_key,
            attempted_url=request.path,
            attempted_id=target_user_id,
            current_id=current_user.id,
        ), 403
    return None


# =====================================================================
# Auth básico
# =====================================================================

@auth_bp.route('/register', methods=['GET', 'POST'])
def register():
    if current_user.is_authenticated:
        return redirect(url_for('tasks.index'))
    form = RegisterForm()
    if form.validate_on_submit():
        user = User(username=form.username.data, email=form.email.data,
                    display_name=form.username.data)
        user.set_password(form.password.data)
        db.session.add(user)
        db.session.commit()
        flash('Conta criada com sucesso. Faça login para continuar.', 'success')
        return redirect(url_for('auth.login'))
    return render_template('auth/register.html', form=form, title='Criar conta')


@auth_bp.route('/login', methods=['GET', 'POST'])
def login():
    if current_user.is_authenticated:
        return redirect(url_for('tasks.index'))
    form = LoginForm()
    if form.validate_on_submit():
        user = User.query.filter_by(email=form.email.data).first()
        if user and user.check_password(form.password.data):
            login_user(user)
            flash(f'Bem-vindo de volta, {user.username}.', 'success')
            return redirect(request.args.get('next') or url_for('tasks.index'))
        flash('E-mail ou senha incorretos.', 'danger')
    return render_template('auth/login.html', form=form, title='Entrar')


@auth_bp.route('/logout')
@login_required
def logout():
    logout_user()
    flash('Você saiu da sua conta.', 'info')
    return redirect(url_for('auth.login'))


# =====================================================================
# Perfil — atalho para o próprio
# =====================================================================

@auth_bp.route('/profile')
@login_required
def profile():
    """Atalho que leva ao perfil do usuário logado."""
    return redirect(url_for('auth.view_profile', user_id=current_user.id))


# =====================================================================
# /profile/<id> — VULNERÁVEL: IDOR (visualizar perfil de qualquer usuário)
# =====================================================================

@auth_bp.route('/profile/<int:user_id>')
@login_required
def view_profile(user_id):
    blocked = _idor_block(user_id, 'idor_profile_view')
    if blocked:
        return blocked

    # ============================================================
    # VULNERABILIDADE: IDOR — não verifica se user_id == current_user.id
    # Qualquer usuário logado pode ver perfil de qualquer outro.
    # CORREÇÃO: verificar dono (já implementada via _idor_block quando
    # patched=True).
    # ============================================================
    user = User.query.get_or_404(user_id)
    stats = user.stats()
    return render_template('auth/view_profile.html', user=user, stats=stats,
                           title=f'Perfil de {user.username}')


# =====================================================================
# /profile/<id>/edit — VULNERÁVEL: IDOR + Upload Inseguro
# =====================================================================

@auth_bp.route('/profile/<int:user_id>/edit', methods=['GET', 'POST'])
@login_required
def edit_profile(user_id):
    blocked = _idor_block(user_id, 'idor_profile_edit')
    if blocked:
        return blocked

    user = User.query.get_or_404(user_id)
    form = EditProfileForm(obj=user)

    if form.validate_on_submit():
        user.display_name = form.display_name.data
        user.bio = form.bio.data
        user.twofa_enabled = form.twofa_enabled.data

        # ============================================================
        # VULNERABILIDADE: Upload inseguro
        # - Aceita qualquer extensão (não há FileAllowed)
        # - Usa filename do cliente sem secure_filename quando vulnerável
        # CORREÇÃO: validar extensão + secure_filename + limitar tamanho
        # ============================================================
        if form.profile_pic.data:
            f = form.profile_pic.data
            if is_patched():
                # versão segura
                from werkzeug.utils import secure_filename as _sf
                safe = _sf(f.filename)
                if not safe or '.' not in safe:
                    flash('Arquivo inválido.', 'danger')
                    return redirect(request.url)
                ext = safe.rsplit('.', 1)[1].lower()
                if ext not in ('jpg', 'jpeg', 'png', 'gif', 'webp'):
                    flash('Apenas imagens são permitidas.', 'danger')
                    return redirect(request.url)
                final = f'user_{user.id}.{ext}'
                f.save(str(UPLOAD_DIR / final))
                user.profile_pic = final
            else:
                # versão vulnerável: usa filename do cliente direto
                filename = f.filename or 'upload.bin'
                f.save(str(UPLOAD_DIR / filename))
                user.profile_pic = filename

        db.session.commit()
        flash('Perfil atualizado.', 'success')
        return redirect(url_for('auth.view_profile', user_id=user.id))

    return render_template('auth/edit_profile.html', form=form, user=user,
                           title=f'Editar perfil de {user.username}')


# =====================================================================
# /profile/<id>/change-password — VULNERÁVEL: IDOR + Bypass 2FA
# =====================================================================

@auth_bp.route('/profile/<int:user_id>/change-password', methods=['GET', 'POST'])
@login_required
def change_password(user_id):
    blocked = _idor_block(user_id, 'twofa_bypass')
    if blocked:
        return blocked

    user = User.query.get_or_404(user_id)
    form = ChangePasswordForm()

    # ============================================================
    # VULNERABILIDADE: Bypass de 2FA via parâmetro de URL
    # Um "modo de admin" foi deixado por engano em produção. Qualquer
    # request com ?bypass_2fa=admin_override_2024 pula a checagem do
    # código 2FA mesmo se o usuário-alvo tem 2FA ativo.
    # CORREÇÃO: remover esse parâmetro completamente.
    # ============================================================
    bypass_2fa = (request.args.get('bypass_2fa') == TWOFA_BACKDOOR
                  and not is_patched())

    # Gera mock OTP na primeira visita (em produção iria por e-mail/SMS)
    if request.method == 'GET' and user.twofa_enabled and not bypass_2fa:
        otp = f'{random.randint(0, 999999):06d}'
        session['mock_otp'] = otp
        flash(f'Código 2FA enviado: {otp} (mock — em produção iria por e-mail)',
              'info')

    if form.validate_on_submit():
        if user.twofa_enabled and not bypass_2fa:
            expected = session.get('mock_otp')
            if not form.otp_code.data or form.otp_code.data != expected:
                flash('Código 2FA inválido ou ausente.', 'danger')
                return render_template('auth/change_password.html',
                                       form=form, user=user,
                                       require_otp=True,
                                       bypass_2fa=False)

        user.set_password(form.new_password.data)
        db.session.commit()
        session.pop('mock_otp', None)

        if bypass_2fa:
            flash(f'Senha de {user.username} alterada (2FA pulado via backdoor).',
                  'warning')
        else:
            flash(f'Senha de {user.username} alterada com sucesso.', 'success')

        return redirect(url_for('auth.view_profile', user_id=user.id))

    return render_template('auth/change_password.html', form=form, user=user,
                           require_otp=user.twofa_enabled and not bypass_2fa,
                           bypass_2fa=bypass_2fa,
                           title='Alterar senha')
