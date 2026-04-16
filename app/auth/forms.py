from flask_wtf import FlaskForm
from wtforms import StringField, PasswordField, SubmitField
from wtforms.validators import DataRequired, Email, EqualTo, Length, ValidationError
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
    confirm_password = PasswordField('Confirmar Senha', validators=[
        DataRequired(message='Confirme sua senha.'),
        EqualTo('password', message='As senhas não coincidem.')
    ])
    submit = SubmitField('Criar Conta')

    def validate_username(self, username):
        user = User.query.filter_by(username=username.data).first()
        if user:
            raise ValidationError('Este nome de usuário já está em uso.')

    def validate_email(self, email):
        user = User.query.filter_by(email=email.data).first()
        if user:
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
