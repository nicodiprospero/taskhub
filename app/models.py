from datetime import datetime, date
from flask_login import UserMixin
from werkzeug.security import generate_password_hash, check_password_hash
from app import db, login_manager


@login_manager.user_loader
def load_user(user_id):
    return User.query.get(int(user_id))


# Tabela de associação Task <-> Tag (many-to-many)
task_tags = db.Table(
    'task_tags',
    db.Column('task_id', db.Integer, db.ForeignKey('task.id'), primary_key=True),
    db.Column('tag_id', db.Integer, db.ForeignKey('tag.id'), primary_key=True),
)


class User(UserMixin, db.Model):
    __tablename__ = 'user'

    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(64), unique=True, nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password_hash = db.Column(db.String(256), nullable=False)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    display_name = db.Column(db.String(80), nullable=True)
    bio = db.Column(db.Text, nullable=True)
    profile_pic = db.Column(db.String(200), nullable=True)
    twofa_enabled = db.Column(db.Boolean, nullable=False, default=False)

    tasks = db.relationship('Task', backref='owner', lazy='dynamic',
                            cascade='all, delete-orphan')
    tags = db.relationship('Tag', backref='owner', lazy='dynamic',
                           cascade='all, delete-orphan')

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        return check_password_hash(self.password_hash, password)

    def stats(self):
        total = self.tasks.count()
        done = self.tasks.filter_by(status='done').count()
        in_progress = self.tasks.filter_by(status='in_progress').count()
        pending = self.tasks.filter_by(status='pending').count()
        overdue = self.tasks.filter(
            Task.due_date < date.today(),
            Task.status != 'done',
            Task.due_date.isnot(None)
        ).count()
        completion_rate = round((done / total) * 100) if total > 0 else 0
        return {
            'total': total,
            'done': done,
            'in_progress': in_progress,
            'pending': pending,
            'overdue': overdue,
            'completion_rate': completion_rate,
        }

    def __repr__(self):
        return f'<User {self.username}>'


class Task(db.Model):
    __tablename__ = 'task'

    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(140), nullable=False)
    description = db.Column(db.Text, nullable=True)
    status = db.Column(db.String(20), nullable=False, default='pending')
    priority = db.Column(db.String(10), nullable=False, default='medium')
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)
    updated_at = db.Column(db.DateTime, nullable=False,
                           default=datetime.utcnow, onupdate=datetime.utcnow)
    due_date = db.Column(db.Date, nullable=True)
    completed_at = db.Column(db.DateTime, nullable=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)

    tags = db.relationship('Tag', secondary=task_tags, lazy='joined',
                           backref=db.backref('tasks', lazy='dynamic'))

    @property
    def is_overdue(self):
        if self.due_date is None or self.status == 'done':
            return False
        return self.due_date < date.today()

    @property
    def days_until_due(self):
        if self.due_date is None:
            return None
        delta = (self.due_date - date.today()).days
        return delta

    def mark_status(self, new_status):
        old_status = self.status
        self.status = new_status
        if new_status == 'done' and old_status != 'done':
            self.completed_at = datetime.utcnow()
        elif new_status != 'done':
            self.completed_at = None

    def __repr__(self):
        return f'<Task {self.title}>'


class Tag(db.Model):
    __tablename__ = 'tag'

    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(40), nullable=False)
    color = db.Column(db.String(20), nullable=False, default='gray')
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)

    __table_args__ = (
        db.UniqueConstraint('name', 'user_id', name='_user_tag_uc'),
    )

    def __repr__(self):
        return f'<Tag {self.name}>'
