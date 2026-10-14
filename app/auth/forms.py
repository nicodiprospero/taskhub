from flask_wtf import FlaskForm
from flask_wtf.file import FileField, FileAllowed
from wtforms import (StringField, PasswordField, SubmitField, TextAreaField,
                     BooleanField)
from wtforms.validators import (DataRequired, Email, EqualTo, Length,
                                ValidationError, Optional)
from app.models import User


class RegisterForm(FlaskForm):
    username = StringField('Usuário', validators=[
        DataRequired(message='O nome de usuário é obrigatório.'),
        Length(min=3, max=64, message='O usuário deve ter entre 3 e 64 caracteres.')
    ])
    email = StringField('E-mail', validators=[
        DataRequired(message='O e-mail é obrigatório.'),
        Email(message='Digite um e-mail válido.')
    ])
    password = PasswordField('Senha', validators=[
        DataRequired(message='A senha é obrigatória.'),
        Length(min=6, message='A senha deve ter no mínimo 6 caracteres.')
    ])
    confirm_password = PasswordField('Confirmar senha', validators=[
        DataRequired(message='Confirme sua senha.'),
        EqualTo('password', message='As senhas não coincidem.')
    ])
    submit = SubmitField('Criar conta')

    def validate_username(self, username):
        if User.query.filter_by(username=username.data).first():
            raise ValidationError('Este nome de usuário já está em uso.')

    def validate_email(self, email):
        if User.query.filter_by(email=email.data).first():
            raise ValidationError('Este e-mail já está cadastrado.')


class LoginForm(FlaskForm):
    email = StringField('E-mail', validators=[
        DataRequired(message='O e-mail é obrigatório.'),
        Email(message='Digite um e-mail válido.')
    ])
    password = PasswordField('Senha', validators=[
        DataRequired(message='A senha é obrigatória.')
    ])
    submit = SubmitField('Entrar')


class EditProfileForm(FlaskForm):
    display_name = StringField('Nome de exibição', validators=[
        Optional(), Length(max=80)
    ])
    bio = TextAreaField('Bio', validators=[
        Optional(), Length(max=500)
    ])
    profile_pic = FileField('Foto de perfil', validators=[
        Optional(),
        # NOTA: na versão vulnerável, NÃO usamos FileAllowed (aceita qualquer extensão).
        # Na versão corrigida, isso seria adicionado:
        # FileAllowed(['jpg', 'jpeg', 'png', 'gif'], 'Apenas imagens são permitidas.')
    ])
    twofa_enabled = BooleanField('Ativar autenticação em 2 fatores (2FA)')
    submit = SubmitField('Salvar alterações')


class ChangePasswordForm(FlaskForm):
    new_password = PasswordField('Nova senha', validators=[
        DataRequired(message='Digite a nova senha.'),
        Length(min=6, message='A senha deve ter no mínimo 6 caracteres.')
    ])
    confirm_new_password = PasswordField('Confirmar nova senha', validators=[
        DataRequired(message='Confirme a nova senha.'),
        EqualTo('new_password', message='As senhas não coincidem.')
    ])
    otp_code = StringField('Código 2FA (6 dígitos)', validators=[
        Optional(), Length(min=6, max=6)
    ])
    submit = SubmitField('Alterar senha')
