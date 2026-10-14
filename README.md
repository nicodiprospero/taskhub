<div align="center">

# TaskHub

**A deliberately vulnerable Flask task manager for teaching broken access control (OWASP A01). It includes a one-command toggle between the vulnerable and the fixed version.**

![Python](https://img.shields.io/badge/Python-3.11-3776AB?logo=python&logoColor=white)
![Flask](https://img.shields.io/badge/Flask-3-000000?logo=flask&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-SQLAlchemy-003B57?logo=sqlite&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-compose-2496ED?logo=docker&logoColor=white)
![OWASP](https://img.shields.io/badge/OWASP-A01%20Broken%20Access%20Control-000000)

🇧🇷 [Leia em português](README.pt-BR.md)

</div>

---

> ⚠️ **Educational project.** The vulnerable mode exists on purpose for classroom demos. Never deploy it on the internet.

TaskHub is a complete task manager: auth, CRUD, tags, filters, Kanban view, profiles and photo upload. It was built for the **Coding for Security** course at FIAP (group project, 4 members). It ships with intentional access-control flaws, and the same flaws can be **patched and un-patched at runtime**. That lets a live demo show the exploit, run `fix.py`, and show the same URL now refused, with no code edits on stage.

## The vulnerabilities (vulnerable mode)

| # | Flaw | Where |
|---|---|---|
| 1 | **IDOR**: edit/delete another user's private task | `/tasks/edit/<id>`, `/tasks/delete/<id>` |
| 2 | **IDOR**: view another user's profile | `/profile/<id>` |
| 3 | **IDOR**: edit another user's profile | `/profile/<id>/edit` |
| 4 | **2FA bypass + IDOR** on password change (simulated 2FA) | `/profile/<id>/change-password` |
| 5 | **Insecure file upload** (no extension check or filename sanitization) | profile photo |

Demo flow: Bob logs into his own account and reaches Alice's resources just by changing the ID in the URL.

## The fix

```bash
python fix.py      # PATCHED: ownership enforced server-side, uploads validated
python unfix.py    # back to VULNERABLE to repeat the demo
```

The core rule the fix enforces: **being logged in is not authorization. The backend must verify that the resource belongs to the requester.** In patched mode, every exploit URL lands on a page explaining the vulnerability and how it was fixed.

The flag is persisted in `.security_state` (or `SECURITY_STATE_FILE` in Docker), so it survives restarts.

## Features

Register / login / logout (Flask-Login, CSRF protection via Flask-WTF) · task CRUD · per-user tags · filters and search · list and Kanban views · light/dark theme · profile with stats · photo upload · bulk delete · overdue highlighting.

## Run it

```bash
pip install -r requirements.txt
python reset.py     # recreate DB, seed Alice & Bob, set VULNERABLE mode
python start.py     # start the server and print the demo walkthrough
# → http://127.0.0.1:5000
```

**Docker:**

```bash
docker compose up -d --build                 # → http://localhost:5000
docker compose exec taskhub python fix.py    # toggle inside the container
```

Demo accounts: `alice@taskhub.com` / `123456` and `bob@taskhub.com` / `123456`.

## Project structure

```
taskhub/
├── app/
│   ├── auth/          # register, login, profile, password change
│   ├── tasks/         # CRUD, filters, Kanban
│   ├── security.py    # runtime PATCHED / VULNERABLE flag
│   ├── models.py
│   └── templates/     # incl. security/fixed.html
├── fix.py · unfix.py · reset.py · start.py · seed.py
├── Dockerfile · docker-compose.yml · docker-entrypoint.sh
├── RELATORIOfinal.md  # full write-up (PT-BR)
└── APRESENTACAO.md    # presentation guide (PT-BR)
```

---

<sub>Built by **Nicolas Di Prospero** and team · Cybersecurity @ FIAP · [github.com/nicodiprospero](https://github.com/nicodiprospero)</sub>
