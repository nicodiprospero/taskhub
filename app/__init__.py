from flask import Flask
from flask_sqlalchemy import SQLAlchemy
from flask_login import LoginManager
from flask_wtf.csrf import CSRFProtect
from config import Config

db = SQLAlchemy()
login_manager = LoginManager()
login_manager.login_view = 'auth.login'
login_manager.login_message = 'Por favor, faça login para acessar esta página.'
login_manager.login_message_category = 'warning'
csrf = CSRFProtect()


def create_app(config_class=Config):
    app = Flask(__name__)
    app.config.from_object(config_class)

    db.init_app(app)
    login_manager.init_app(app)
    csrf.init_app(app)

    from app.auth import auth_bp
    from app.tasks import tasks_bp

    app.register_blueprint(auth_bp)
    app.register_blueprint(tasks_bp)

    # Atalhos sem prefixo /auth para os exploits ficarem mais limpos na demo
    from app.auth import routes as auth_routes
    app.add_url_rule('/profile/<int:user_id>', view_func=auth_routes.view_profile, methods=['GET'])
    app.add_url_rule('/profile/<int:user_id>/edit', view_func=auth_routes.edit_profile, methods=['GET', 'POST'], endpoint='edit_profile_short')
    app.add_url_rule('/profile/<int:user_id>/change-password', view_func=auth_routes.change_password, methods=['GET', 'POST'], endpoint='change_password_short')

    with app.app_context():
        db.create_all()

    return app
