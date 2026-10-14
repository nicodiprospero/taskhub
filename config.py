import os

BASE_DIR = os.path.abspath(os.path.dirname(__file__))
DATA_DIR = os.environ.get('DATA_DIR') or BASE_DIR

# Garante que o diretorio de dados existe
os.makedirs(DATA_DIR, exist_ok=True)


class Config:
    SECRET_KEY = os.environ.get('SECRET_KEY') or 'taskhub-secret-key-dev-2024'
    SQLALCHEMY_DATABASE_URI = 'sqlite:///' + os.path.join(DATA_DIR, 'taskhub.db')
    SQLALCHEMY_TRACK_MODIFICATIONS = False
    WTF_CSRF_ENABLED = True
    MAX_CONTENT_LENGTH = 5 * 1024 * 1024  # 5 MB upload max
