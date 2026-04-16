from flask_wtf import FlaskForm
from wtforms import StringField, TextAreaField, SelectField, SubmitField
from wtforms.validators import DataRequired, Length


class TaskForm(FlaskForm):
    title = StringField('Título', validators=[
        DataRequired(message='O título é obrigatório.'),
        Length(min=3, max=140, message='O título deve ter entre 3 e 140 caracteres.')
    ])
    description = TextAreaField('Descrição', validators=[
        Length(max=1000, message='A descrição pode ter no máximo 1000 caracteres.')
    ])
    status = SelectField('Status', choices=[
        ('pending', 'Pendente'),
        ('in_progress', 'Em Progresso'),
        ('done', 'Concluída')
    ], validators=[DataRequired()])
    priority = SelectField('Prioridade', choices=[
        ('low', 'Baixa'),
        ('medium', 'Média'),
        ('high', 'Alta')
    ], validators=[DataRequired()])
    submit = SubmitField('Salvar Tarefa')
