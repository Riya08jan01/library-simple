# LibraSys — Library Management System

A lightweight, single-file Java library management server built on `com.sun.net.httpserver` (no Tomcat or external application server required). Includes admin and reader portals, book catalog, member management, book borrowing/returning with fines, and an admin dashboard with analytics.

## 🚀 Features

- 🔐 **Dual login portals** — Admin and Reader authentication
- 📚 **Book catalog** — Add, update, delete, search, and filter books by category
- 👥 **Member management** — Register and manage library members
- 📖 **Book issue & return** — Track borrowings, due dates, and returns
- 💰 **Fine tracking** — Calculate and display overdue fines
- 📊 **Admin dashboard** — Stats, most-borrowed books, category breakdown, active issues
- 🌐 **Runs on `http://localhost:8080`** — Built-in Java HTTP server

## 🛠 Tech Stack

- **Language:** Java (JDK 21+)
- **Server:** `com.sun.net.httpserver.HttpServer` (built-in, no external server)
- **Build:** `javac` / `java` (via `compile.bat` and `run.bat`)
- **Database schema:** SQL (see `schema.sql`)
- **Storage:** In-memory (Java collections) — `schema.sql` is provided for porting to a persistent DB

## 📁 Project Structure

```
library-simple/
├── LibraryServer.java      # Main server source (single file, ~878 lines)
├── compile.bat             # Windows compile script
├── run.bat                 # Windows compile + run script
├── schema.sql              # Database schema (SQL)
├── README.md               # This file
├── .gitignore              # Git ignore rules
└── LibraryManagement/
    └── PROJECT_REPORT.md   # Project documentation report
```

> Compiled `.class` files, runtime logs (`out.txt`, `err.txt`, `runlog.txt`), and working-copy variants (`LibraryServer.broken.java`, `LibraryServer.tangled.java`) are excluded via `.gitignore`.

## 🗄 Database Schema (`schema.sql`)

The `schema.sql` file contains the relational schema for the application's data model (`Book`, `Member`, `Issue`) plus seed data matching the default credentials and sample catalog.

**Tables:**
- `books` — id, title, author, isbn, category, total_copies, available_copies, cover_url
- `members` — id, name, email, password, phone, address, join_date, status
- `issues` — id, book_id, member_id, book_title, member_name, issue_date, due_date, return_date, fine, status

Run `schema.sql` in your SQL database (MySQL, PostgreSQL, SQLite, etc.) to create the tables and insert seed data.

## ⚡ Quick Start (Windows)

1. **Make sure JDK 21+ is installed** and `javac`/`java` are on your PATH.
2. **Run the server:**
   ```powershell
   cd library-simple
   .\run.bat
   ```
3. **Open your browser:** `http://localhost:8080`

### Default Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | `admin@libra.com` | `admin123` |
| Reader | `john@email.com` | `john123` |

- **Admin portal:** `http://localhost:8080/admin/login`
- **Reader portal:** `http://localhost:8080/reader/login`

> Keep the `run.bat` window open — closing it stops the server.

## 🔧 Manual Build & Run

```powershell
javac LibraryServer.java
java LibraryServer
```

## 📋 API Endpoints (Selected)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Landing page |
| GET | `/login` | Login page |
| POST | `/login` | Authenticate |
| GET | `/admin/login` | Admin login |
| GET | `/reader/login` | Reader login |
| GET | `/dashboard` | Admin dashboard |
| GET | `/books` | Book catalog |
| POST | `/books` | Add/update book |
| GET | `/members` | Member list |
| GET | `/issues` | Issue/borrowing records |
| GET | `/logout` | Logout |

## 📄 License

This project is provided as-is for educational and personal use.

## 🤝 Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.