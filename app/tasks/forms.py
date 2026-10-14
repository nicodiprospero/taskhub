from flask_wtf import FlaskForm
from wtforms import StringField, TextAreaField, SelectField, SubmitField, DateField
from wtforms.validators import DataRequired, Length, Optional


class TaskForm(FlaskForm):
    title = StringField('Título', validators=[
        DataRequired(message='O título é obrigatório.'),
        Length(min=3, max=140, message='O título deve ter entre 3 e 140 caracteres.')
    ])
    description = TextAreaField('Descrição', validators=[
        Optional(),
        Length(max=2000, message='A descrição pode ter no máximo 2000 caracteres.')
    ])
    status = SelectField('Status', choices=[
        ('pending', 'Pendente'),
        ('in_progress', 'Em progresso'),
        ('done', 'Concluída')
    ], validators=[DataRequired()])
    priority = SelectField('Prioridade', choices=[
        ('low', 'Baixa'),
        ('medium', 'Média'),
        ('high', 'Alta')
    ], validators=[DataRequired()])
    due_date = DateField('Data de entrega', validators=[Optional()])
    tags = StringField('Tags (separadas por vírgula)', validators=[
        Optional(),
        Length(max=200)
    ])
    submit = SubmitField('Salvar tarefa')
