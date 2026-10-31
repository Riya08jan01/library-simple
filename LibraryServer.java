import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * LibraSys — Library Management System
 * Single-file server built on Java's built-in HttpServer (no Tomcat needed).
 * Compile: javac LibraryServer.java   Run: java LibraryServer   (or run.bat)
 * Login: admin@libra.com / admin123 (admin) · john@email.com / john123 (user)
 */
public class LibraryServer {

    /* ============================ DATA MODEL ============================ */
    static class Book { int id; String title, author, isbn, category; int totalCopies, availableCopies; String coverUrl; }
    static class Member { int id; String name, email, password, phone, address, joinDate, status; }
    static class Issue { int id; int bookId, memberId; String bookTitle, memberName; String issueDate, dueDate, returnDate; double fine; String status; }

    static List<Book> books = new ArrayList<>();
    static List<Member> members = new ArrayList<>();
    static List<Issue> issues = new ArrayList<>();
    static int nextBookId = 1, nextMemberId = 1, nextIssueId = 1;

    /* ============================ SEED DATA ============================ */
    static {
        addBook(1, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", "Fiction", 3, 3);
        addBook(2, "To Kill a Mockingbird", "Harper Lee", "978-0061120084", "Fiction", 2, 2);
        addBook(3, "1984", "George Orwell", "978-0451524935", "Science Fiction", 4, 4);
        addBook(4, "Clean Code", "Robert C. Martin", "978-0132350884", "Technology", 3, 3);
        addBook(5, "Atomic Habits", "James Clear", "978-0735211292", "Self Help", 5, 5);
        addBook(6, "Sapiens", "Yuval Noah Harari", "978-0062316097", "History", 3, 3);
        addBook(7, "The Hobbit", "J.R.R. Tolkien", "978-0547928227", "Fantasy", 3, 3);
        addBook(8, "A Brief History of Time", "Stephen Hawking", "978-0553380163", "Science", 2, 2);
        addBook(9, "Harry Potter and the Sorcerer's Stone", "J.K. Rowling", "978-0439708184", "Fantasy", 4, 4);
        addBook(10, "The Alchemist", "Paulo Coelho", "978-0062315007", "Fiction", 3, 3);
        addBook(11, "Head First Java", "Kathy Sierra", "978-1491910771", "Technology", 2, 2);
        addBook(12, "Pride and Prejudice", "Jane Austen", "978-0141439518", "Fiction", 2, 2);
        addBook(13, "The Diary of a Young Girl", "Anne Frank", "978-0553296983", "History", 2, 2);
        addBook(14, "Introduction to Algorithms", "Thomas H. Cormen", "978-0262046305", "Technology", 2, 2);
        addBook(15, "Rich Dad Poor Dad", "Robert T. Kiyosaki", "978-1612680194", "Self Help", 3, 3);
        addBook(16, "Cosmos", "Carl Sagan", "978-0345539434", "Science", 2, 2);
        addMember(1, "John Doe", "john@email.com", "john123", "555-1234", "123 Main Street", "2024-01-15", "active");
        addMember(2, "Jane Smith", "jane@email.com", "jane123", "555-5678", "456 Oak Avenue", "2024-02-20", "active");
        addMember(3, "Admin User", "admin@libra.com", "admin123", "555-0000", "Library HQ", "2023-01-01", "active");
        addIssue(1, 1, 1, "The Great Gatsby", "John Doe", "2024-10-01", "2024-10-15", null, 0.0, "issued");
        for (Book b : books) if (b.id == 1) b.availableCopies--;
        nextBookId = 17; nextMemberId = 4; nextIssueId = 2;
    }

    static void addBook(int id, String t, String a, String i, String c, int tc, int ac) {
        Book b = new Book(); b.id = id; b.title = t; b.author = a; b.isbn = i; b.category = c; b.totalCopies = tc; b.availableCopies = ac;
        b.coverUrl = "https://covers.openlibrary.org/b/isbn/" + i.replace("-", "") + "-M.jpg";
        books.add(b);
    }
    static void addMember(int id, String n, String e, String p, String ph, String a, String jd, String st) {
        Member m = new Member(); m.id = id; m.name = n; m.email = e; m.password = p; m.phone = ph; m.address = a; m.joinDate = jd; m.status = st; members.add(m);
    }
    static void addIssue(int id, int bId, int mId, String bt, String mn, String idt, String dd, String rd, double f, String st) {
        Issue i = new Issue(); i.id = id; i.bookId = bId; i.memberId = mId; i.bookTitle = bt; i.memberName = mn;
        i.issueDate = idt; i.dueDate = dd; i.returnDate = rd; i.fine = f; i.status = st; issues.add(i);
    }
    static Book findBook(int id) { for (Book b : books) if (b.id == id) return b; return null; }
    static Member findMember(int id) { for (Member m : members) if (m.id == id) return m; return null; }
    static Member findMemberByName(String name) {
        try { name = URLDecoder.decode(name, StandardCharsets.UTF_8); } catch (Exception ignored) {}
        for (Member m : members) if (m.name.equals(name)) return m;
        return null;
    }

    /* ============================ HTTP PLUMBING ============================ */
    static boolean isLoggedIn(HttpExchange e) {
        String c = e.getRequestHeaders().getFirst("Cookie");
        return c != null && c.contains("session=");
    }
    static String getCookie(HttpExchange e, String key) {
        String c = e.getRequestHeaders().getFirst("Cookie");
        if (c == null) return "";
        for (String p : c.split(";")) { p = p.trim(); if (p.startsWith(key + "=")) return p.substring(key.length() + 1); }
        return "";
    }
    static String getParam(HttpExchange e, String key) {
        String q = e.getRequestURI().getRawQuery();
        if (q == null) return "";
        for (String kv : q.split("&")) {
            String[] a = kv.split("=", 2);
            if (a.length == 2 && a[0].equals(key)) { try { return URLDecoder.decode(a[1], "UTF-8"); } catch (Exception ex) { return a[1]; } }
        }
        return "";
    }
    static String urlEncode(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }
    static void sendHtml(HttpExchange e, String h) throws IOException {
        byte[] b = h.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        e.sendResponseHeaders(200, b.length);
        e.getResponseBody().write(b); e.getResponseBody().close();
    }
    static void sendRedirect(HttpExchange e, String l) throws IOException {
        e.getResponseHeaders().set("Location", l); e.sendResponseHeaders(302, -1); e.getResponseBody().close();
    }
    static Map<String, String> parseForm(InputStream body) throws IOException {
        Map<String, String> p = new HashMap<>();
        String s = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        for (String kv : s.split("&")) {
            String[] a = kv.split("=", 2);
            if (a.length == 2) p.put(URLDecoder.decode(a[0], StandardCharsets.UTF_8), URLDecoder.decode(a[1], StandardCharsets.UTF_8));
        }
        return p;
    }
    static String escape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '&') b.append("&amp;");
            else if (ch == '<') b.append("&lt;");
            else if (ch == '>') b.append("&gt;");
            else if (ch == '"') b.append("&#34;");
            else b.append(ch);
        }
        return b.toString();
    }
    static String escapeJs(String s) { if (s == null) return ""; return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\""); }
    static String quoteJs(String s) { return "'" + escapeJs(s == null ? "" : s) + "'"; }
    static String currency(double d) { return "₹ " + String.format("%.2f", d); }

    /* ============================ HTML SHELL ============================ */
    static String page(String title, String body, boolean loggedIn, String role, String activePage) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<title>" + title + " - LibraSys</title>" +
            "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css\" rel=\"stylesheet\">" +
            "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/font/bootstrap-icons.css\" rel=\"stylesheet\">" +
            "<style>" + cssAll() + "</style></head><body>" +
            (loggedIn ? sidebar(role, activePage) : "") +
            renderBody(title, body, loggedIn, role) +
            "<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js\"></script>" +
            "<script>" + js() + "</script></body></html>";
    }

    static String renderBody(String title, String body, boolean loggedIn, String role) {
        if (!loggedIn) {
            return "<div class=\"login-wrap\"><div class=\"login-card\">" +
                "<div class=\"brand\"><h1><i class=\"bi bi-book-half\"></i>LibraSys</h1><p>Library Management System</p></div>" +
                body + "</div></div>";
        }
        return "<div class=\"main-content " + role + "\">" +
            "<header class=\"topbar " + role + "\"><h2><i class=\"bi bi-book-half\"></i>" + title + "</h2>" +
            "<div class=\"d-flex align-items-center gap-3\">" +
            "<span class=\"text-muted small\">Welcome, <strong>" + escape(getCookie(current(), "name")) + "</strong></span>" +
            "<a href=\"/logout\" class=\"btn btn-outline\"><i class=\"bi bi-box-arrow-right\"></i> Logout</a>" +
            "</div></header>" + body + "</div>" +
            "<div class=\"footer\">LibraSys · a tiny library, beautifully run.</div>";
    }
    /* the exchange currently being served (used for cookie lookup in shared fragments) */
    static final ThreadLocal<HttpExchange> CURRENT = new ThreadLocal<>();
    static HttpExchange current() { return CURRENT.get(); }

    static String sidebar(String role, String activePage) {
        boolean isAdmin = "admin".equals(role);
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"sidebar ").append(role).append("\">");
        sb.append("<div class=\"brand\"><i class=\"bi bi-book-half\"></i>LibraSys</div>");
        sb.append("<nav>");
        if (isAdmin) {
            sb.append(navItem("/dashboard", "bi-speedometer2", "Dashboard", activePage));
            sb.append(navItem("/books", "bi-book", "Books", activePage));
            sb.append(navItem("/members", "bi-people", "Members", activePage));
            sb.append(navItem("/issues", "bi-journal-bookmark", "Issues", activePage));
        } else {
            sb.append(navItem("/user-dashboard", "bi-speedometer2", "My Dashboard", activePage));
            sb.append(navItem("/user-books", "bi-book", "Browse Shelves", activePage));
            sb.append(navItem("/user-issues", "bi-journal-bookmark", "My Issues", activePage));
        }
        sb.append("</nav>");
        sb.append("<div class=\"user-card\">");
        String nm = getCookie(current(), "name");
        sb.append("<div class=\"user-avatar\">").append(nm.isEmpty() ? "?" : Character.toUpperCase(nm.charAt(0))).append("</div>");
        sb.append("<div class=\"user-name\">").append(escape(nm)).append("</div>");
        sb.append("<div class=\"user-role\">").append(role).append("</div>");
        sb.append("</div></nav>");
        return sb.toString();
    }
    static String navItem(String href, String icon, String label, String activePage) {
        return "<div class=\"nav-item " + (href.equals(activePage) ? "active" : "") +
            "\" onclick=\"location.href='" + href + "'\"><i class=\"bi " + icon + "\"></i> " + label + "</div>";
    }
    static String js() { return "function confirmDelete(){return confirm('Are you sure?');}function closeModal(id){document.getElementById(id).classList.remove('show');}"; }

    /* ============================ CSS ============================ */
    static String cssAll() { return cssBase() + cssAdmin() + cssUser() + cssCommon(); }

    static String cssBase() {
        return "@import url('https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,600;9..144,700&family=Inter:wght@400;500;600;700;800&display=swap');" +
            ":root{--accent:#40634d;--accent-dark:#2f4b3a;--accent-soft:#e4efe6;--gold:#c48a3a;--gold-soft:#f7ead7;--dark:#2b2118;--light:#faf6ef;--paper:#fffdf8;--border:#e8dfd2;--danger:#b3413a;--warning:#b07d24}" +
            "body{font-family:'Inter','Segoe UI',sans-serif;background:radial-gradient(1200px 400px at 80% -10%,#f3ecdd 0%,transparent 60%),var(--light);margin:0;overflow-x:hidden;color:var(--dark)}" +
            "h1,h2,h3,h4,.brand,.stat-content h3,.table-container h3,.page-flex h3,.modal-header h4,.login-card .brand h1{font-family:'Fraunces',Georgia,serif}" +
            ".sidebar{position:fixed;top:0;left:0;height:100vh;width:260px;background:linear-gradient(180deg,#2b2118 0%,#3a2c1e 100%);color:#f3e9d6;display:flex;flex-direction:column;z-index:1000;box-shadow:4px 0 24px rgba(43,33,24,0.35)}" +
            ".sidebar .brand{padding:1.6rem 1rem;font-size:1.5rem;font-weight:700;color:#f6ecd8;text-align:center;border-bottom:1px solid rgba(196,138,58,0.3);letter-spacing:0.3px}" +
            ".sidebar .brand i{margin-right:0.75rem;color:var(--gold)}" +
            ".sidebar nav{flex:1;padding:1rem 0.75rem}" +
            ".sidebar .nav-item{display:flex;align-items:center;padding:0.85rem 1.1rem;color:#b8a685;font-size:0.95rem;font-weight:500;border-left:3px solid transparent;margin-bottom:4px;border-radius:0 10px 10px 0;transition:all 0.2s;cursor:pointer}" +
            ".sidebar .nav-item i{width:24px;margin-right:0.75rem;font-size:1.1rem;color:#8a7a63}" +
            ".sidebar .nav-item:hover{background:rgba(196,138,58,0.12);color:#f3e9d6}" +
            ".sidebar .nav-item.active{background:linear-gradient(90deg,rgba(196,138,58,0.25),rgba(196,138,58,0.05));color:#f6ecd8;border-left:3px solid var(--gold)}" +
            ".sidebar .nav-item.active i,.sidebar .nav-item:hover i{color:var(--gold)}" +
            ".sidebar .user-card{padding:1.2rem 1rem;border-top:1px solid rgba(196,138,58,0.25);background:rgba(255,255,255,0.03);text-align:center}" +
            ".sidebar .user-avatar{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:1.2rem;color:#f6ecd6;margin:0 auto 0.5rem}" +
            ".sidebar.admin .user-avatar{background:var(--accent)}" +
            ".sidebar.user .user-avatar{background:var(--gold)}" +
            ".sidebar .user-name{font-weight:600;font-size:0.95rem;color:#fff}" +
            ".sidebar .user-role{font-size:0.7rem;text-transform:uppercase;letter-spacing:1px}" +
            ".sidebar.admin .user-role{color:#9cc5ad}" +
            ".sidebar.user .user-role{color:#e3c48a}" +
            ".sidebar.admin .brand{color:#c8e0d0;border-bottom:1px solid rgba(64,99,77,0.6)}" +
            ".sidebar.admin{background:linear-gradient(180deg,#22301f 0%,#2f4b3a 100%)}" +
            ".sidebar.admin .nav-item{color:#a8bfa8}" +
            ".sidebar.admin .nav-item i{color:#7a9a80}" +
            ".sidebar.admin .nav-item:hover{background:rgba(64,99,77,0.35);color:#e8f2ea}" +
            ".sidebar.admin .nav-item.active{background:linear-gradient(90deg,rgba(64,99,77,0.6),rgba(64,99,77,0.15));color:#fff;border-left:3px solid #7fc79b}" +
            ".sidebar.admin .nav-item.active i,.sidebar.admin .nav-item:hover i{color:#7fc79b}";
    }
    static String cssAdmin() {
        return ".topbar.admin{background:var(--paper);border-bottom:3px solid var(--accent)}" +
            ".topbar.admin h2{color:var(--dark)}" +
            ".topbar.admin h2 i{color:var(--accent)}" +
            ".stat-icon.admin-primary{background:linear-gradient(135deg,var(--accent),var(--accent-dark))}";
    }
    static String cssUser() {
        return ".topbar.user{background:var(--paper);border-bottom:3px solid var(--gold)}" +
            ".topbar.user h2{color:var(--dark)}" +
            ".topbar.user h2 i{color:var(--gold)}" +
            ".stat-icon.user-primary{background:linear-gradient(135deg,var(--gold),#a8742e)}" +
            ".sidebar.user .user-role{color:#e3c48a}" +
            ".main-content.user{background:radial-gradient(1000px 400px at 90% 0%,#faf0d6 0%,transparent 55%)}";
    }
    static String cssCommon() {
        return ".main-content{margin-left:260px;padding:2rem 2.5rem 2rem;min-height:100vh}" +
            ".topbar{display:flex;justify-content:space-between;align-items:center;background:var(--paper);padding:1.1rem 1.6rem;border-radius:14px;box-shadow:0 2px 4px rgba(43,33,24,0.05),0 6px 18px rgba(43,33,24,0.07);margin-bottom:2rem;border:1px solid var(--border)}" +
            ".topbar h2{margin:0;font-size:1.5rem;font-weight:700;display:flex;align-items:center;letter-spacing:-0.3px}" +
            ".topbar h2 i{margin-right:0.75rem}" +
            ".stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:1.4rem;margin-bottom:2rem}" +
            ".stat-card{background:var(--paper);padding:1.6rem 1.75rem;border-radius:14px;box-shadow:0 2px 4px rgba(43,33,24,0.05),0 6px 18px rgba(43,33,24,0.07);border:1px solid var(--border);display:flex;align-items:center;gap:1.25rem;transition:transform 0.2s,box-shadow 0.2s;position:relative;overflow:hidden}" +
            ".stat-card:hover{transform:translateY(-4px);box-shadow:0 12px 30px rgba(43,33,24,0.14)}" +
            ".stat-card::before{content:'';position:absolute;left:0;top:0;bottom:0;width:4px;background:var(--accent)}" +
            ".stat-icon{width:60px;height:60px;border-radius:14px;display:flex;align-items:center;justify-content:center;font-size:1.6rem;color:#fff;box-shadow:0 4px 12px rgba(43,33,24,0.2)}" +
            ".stat-icon.secondary{background:linear-gradient(135deg,#3d6b8c,#2c5069)}" +
            ".stat-icon.danger{background:linear-gradient(135deg,var(--danger),#8f332d)}" +
            ".stat-icon.warning{background:linear-gradient(135deg,var(--warning),#8f611b)}" +
            ".stat-content h3{margin:0;font-size:2.1rem;font-weight:700;line-height:1.1;color:var(--dark)}" +
            ".stat-content p{margin:0.3rem 0 0;font-size:0.78rem;color:#6f6257;text-transform:uppercase;letter-spacing:0.8px;font-weight:600}" +
            ".table-container{background:var(--paper);border-radius:14px;box-shadow:0 2px 4px rgba(43,33,24,0.05),0 6px 18px rgba(43,33,24,0.07);border:1px solid var(--border);overflow:hidden;margin-bottom:2rem}" +
            ".table-container h3{padding:1.1rem 1.4rem;margin:0;font-size:1.15rem;border-bottom:1px solid var(--border);display:flex;align-items:center}" +
            ".table-container h3 i{color:var(--gold);margin-right:0.6rem}" +
            ".table{width:100%;border-collapse:collapse;font-size:0.9rem;margin:0}" +
            ".table thead{background:#f5eee0}" +
            ".table th{padding:1rem 1.4rem;font-weight:700;text-transform:uppercase;font-size:0.72rem;letter-spacing:0.9px;color:#6f6257;border-bottom:2px solid var(--border)}" +
            ".table td{padding:1.1rem 1.4rem;border-bottom:1px solid #f0e8d8;vertical-align:middle}" +
            ".table tbody tr:hover{background:#faf4e6}" +
            ".btn{display:inline-flex;align-items:center;gap:0.5rem;padding:0.65rem 1.4rem;border:none;border-radius:999px;font-weight:600;font-size:0.9rem;cursor:pointer;text-decoration:none;transition:all 0.16s;font-family:inherit}" +
            ".btn-primary{background:var(--accent);color:#fdfbf4;box-shadow:0 3px 10px rgba(64,99,77,0.3)}" +
            ".btn-primary:hover{background:var(--accent-dark);transform:translateY(-1px)}" +
            ".btn-success{background:var(--accent);color:#fdfbf4}" +
            ".btn-success:hover{background:var(--accent-dark);transform:translateY(-1px)}" +
            ".btn-danger{background:var(--danger);color:#fff}" +
            ".btn-danger:hover{background:#96352f;transform:translateY(-1px)}" +
            ".btn-outline{background:transparent;color:var(--accent);border:1.5px solid var(--accent)}" +
            ".btn-outline:hover{background:var(--accent-soft)}" +
            ".btn-sm{padding:0.45rem 0.95rem;font-size:0.83rem}" +
            ".form-card{background:var(--paper);border-radius:14px;box-shadow:0 2px 4px rgba(43,33,24,0.05),0 6px 18px rgba(43,33,24,0.07);border:1px solid var(--border);padding:2rem;max-width:650px;margin:0 auto}" +
            ".form-group{margin-bottom:1.4rem}" +
            ".form-group label{display:block;margin-bottom:0.55rem;font-weight:600;font-size:0.88rem;color:var(--dark)}" +
            ".form-control{width:100%;padding:0.8rem 1rem;border:1.5px solid var(--border);border-radius:10px;font-size:0.95rem;background:#fff;font-family:inherit;color:var(--dark)}" +
            ".form-control:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px rgba(64,99,77,0.12)}" +
            ".alert{padding:0.9rem 1.4rem;border-radius:10px;margin-bottom:1.5rem;font-size:0.9rem;font-weight:500}" +
            ".alert-danger{background:#f6e2e0;color:var(--danger);border:1px solid #ecc7c4}" +
            ".alert-success{background:var(--accent-soft);color:var(--accent-dark);border:1px solid #c4dcc9}" +
            ".badge{display:inline-block;padding:0.35rem 0.85rem;border-radius:999px;font-size:0.72rem;font-weight:700;text-transform:uppercase;letter-spacing:0.5px}" +
            ".badge-issued{background:var(--gold-soft);color:#8a5f22}" +
            ".badge-returned{background:var(--accent-soft);color:var(--accent-dark)}" +
            ".badge-active{background:var(--accent-soft);color:var(--accent-dark)}" +
            ".badge-inactive{background:var(--gold-soft);color:#8a5f22}" +
            ".badge-blocked{background:#f6e2e0;color:var(--danger)}" +
            ".badge-available{background:var(--accent-soft);color:var(--accent-dark)}" +
            ".badge-out{background:#f6e2e0;color:var(--danger)}" +
            ".book-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:1.4rem;margin-bottom:2rem}" +
            ".book-card{background:var(--paper);border-radius:14px;overflow:hidden;border:1px solid var(--border);box-shadow:0 2px 4px rgba(43,33,24,0.05),0 6px 18px rgba(43,33,24,0.07);display:flex;flex-direction:column;transition:transform 0.2s,box-shadow 0.2s}" +
            ".book-card:hover{transform:translateY(-5px);box-shadow:0 12px 30px rgba(43,33,24,0.16)}" +
            ".book-cover{height:230px;background:linear-gradient(160deg,#e9dec6,#ddcfb2);overflow:hidden;display:flex;align-items:center;justify-content:center}" +
            ".book-cover img{width:100%;height:100%;object-fit:cover;display:block}" +
            ".book-cover .no-cover{font-family:'Fraunces',Georgia,serif;font-size:1.05rem;color:#5c4d3b;text-align:center;padding:1.5rem;line-height:1.3}" +
            ".book-body{padding:1rem 1.1rem 1.2rem;display:flex;flex-direction:column;gap:0.45rem;flex:1}" +
            ".book-body h4{font-size:1.05rem;margin:0;line-height:1.25;color:var(--dark)}" +
            ".book-body .author{font-size:0.82rem;color:#6f6257;margin:0}" +
            ".book-foot{display:flex;justify-content:space-between;align-items:center;margin-top:auto;padding-top:0.6rem;font-size:0.8rem;color:#6f6257}" +
            ".book-actions{margin-top:0.5rem}" +
            ".book-actions .btn{width:100%;justify-content:center}" +
            ".filter-tabs{display:flex;gap:0.5rem;flex-wrap:wrap;margin-bottom:1.5rem}" +
            ".tab{padding:0.45rem 1.1rem;border-radius:999px;font-size:0.85rem;font-weight:600;color:#6f6257;background:var(--paper);border:1px solid var(--border);text-decoration:none;transition:all 0.15s;cursor:pointer}" +
            ".tab:hover{border-color:var(--accent);color:var(--accent)}" +
            ".tab-active,.tab-active:hover{background:var(--accent);color:#fdfbf4;border-color:var(--accent)}" +
            ".search-bar{display:flex;gap:0.7rem;margin-bottom:1.5rem;flex-wrap:wrap}" +
            ".search-bar input{flex:1;min-width:250px;padding:0.75rem 1.2rem;border:1.5px solid var(--border);border-radius:999px;font-size:0.95rem;font-family:inherit;background:var(--paper);color:var(--dark)}" +
            ".search-bar input:focus{outline:none;border-color:var(--accent)}" +
            ".page-flex{display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem;flex-wrap:wrap;gap:1rem}" +
            ".page-flex h3{margin:0;font-size:1.4rem}" +
            ".page-flex h3 i{color:var(--gold);margin-right:0.6rem}" +
            ".empty-state{grid-column:1/-1;text-align:center;padding:3rem 0;color:#6f6257;font-size:1.05rem;font-style:italic}" +
            ".portal-card{display:flex;flex-direction:column;align-items:center;gap:0.5rem;padding:2.2rem 2.8rem;border-radius:18px;text-decoration:none;transition:transform 0.2s,box-shadow 0.2s;border:1px solid var(--border);background:var(--paper)}" +
            ".portal-card i{font-size:2.4rem}" +
            ".portal-card strong{font-family:'Fraunces',Georgia,serif;font-size:1.3rem;color:var(--dark)}" +
            ".portal-card span{font-size:0.85rem;color:#6f6257}" +
            ".portal-card:hover{transform:translateY(-6px);box-shadow:0 16px 40px rgba(43,33,24,0.2)}" +
            ".portal-admin i{color:var(--accent)}" +
            ".portal-admin:hover{border-color:var(--accent);box-shadow:0 16px 40px rgba(64,99,77,0.3)}" +
            ".portal-reader i{color:var(--gold)}" +
            ".portal-reader:hover{border-color:var(--gold);box-shadow:0 16px 40px rgba(196,138,58,0.3)}" +
            ".portal-badge{display:inline-flex;align-items:center;gap:0.5rem;padding:0.5rem 1.3rem;border-radius:999px;font-weight:700;font-size:0.95rem}" +
            ".badge-admin{background:var(--accent-soft);color:var(--accent-dark)}" +
            ".badge-reader{background:var(--gold-soft);color:#8a5f22}" +
            ".portal-subtitle{color:#6f6257;font-style:italic;margin-top:0.6rem;font-size:0.9rem}" +
            ".portal-btn-admin{background:var(--accent);color:#fdfbf4}" +
            ".portal-btn-user{background:var(--gold);color:#fff}" +
            ".portal-switch{color:var(--accent);font-size:0.88rem;font-weight:600;text-decoration:none}" +
            ".portal-switch:hover{text-decoration:underline}" +
            ".login-wrap{display:flex;align-items:center;justify-content:center;min-height:100vh;background:radial-gradient(1000px 500px at 15% 0%,#4a3f33 0%,transparent 55%),linear-gradient(160deg,#241b12 0%,#3a2c1e 55%,#241b12 100%);padding:1rem}" +
            ".login-card{background:var(--paper);border-radius:20px;box-shadow:0 40px 90px rgba(0,0,0,0.5);padding:3rem;width:100%;max-width:440px;position:relative}" +
            ".login-card::before{content:'';position:absolute;inset:10px;border:1px solid rgba(196,138,58,0.35);border-radius:14px;pointer-events:none}" +
            ".login-card .brand{text-align:center;margin-bottom:2rem;position:relative}" +
            ".login-card .brand h1{margin:0;font-size:2.1rem;font-weight:700;display:flex;align-items:center;justify-content:center;gap:0.7rem;color:var(--dark);letter-spacing:-0.5px}" +
            ".login-card .brand h1 i{color:var(--gold)}" +
            ".login-card .brand p{color:#6f6257;font-size:0.9rem;margin-top:0.5rem;font-style:italic}" +
            ".login-card .text-muted{color:#6f6257 !important}" +
            ".welcome-banner{background:linear-gradient(135deg,var(--gold),#a8742e);color:#fff;border-radius:14px;padding:1.6rem 2rem;margin-bottom:2rem;box-shadow:0 6px 20px rgba(196,138,58,0.35)}" +
            ".welcome-banner h2{margin:0 0 0.3rem;color:#fff;font-size:1.7rem}" +
            ".welcome-banner p{margin:0;color:#fdf3dd;font-style:italic}" +
            ".two-col{display:grid;grid-template-columns:1fr 1fr;gap:1.5rem}" +
            ".two-col .table-container{margin-bottom:2rem}" +
            "@media(max-width:1000px){.two-col{grid-template-columns:1fr}}" +
            ".rec-card{background:var(--paper);border-radius:14px;overflow:hidden;border:1px solid var(--border);box-shadow:0 4px 14px rgba(43,33,24,0.08);display:flex;flex-direction:column}" +
            ".rec-card .book-cover img{width:100%;height:100%;object-fit:cover}" +
            ".serif{font-family:'Fraunces',Georgia,serif}" +
            ".modal{display:none;position:fixed;z-index:2000;left:0;top:0;width:100%;height:100%;background:rgba(36,27,18,0.55);backdrop-filter:blur(3px)}" +
            ".modal.show{display:flex;align-items:center;justify-content:center}" +
            ".modal-content{background:var(--paper);border-radius:16px;padding:2rem;width:100%;max-width:520px;max-height:90vh;overflow-y:auto;border:1px solid var(--border);box-shadow:0 30px 70px rgba(36,27,18,0.4)}" +
            ".modal-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem}" +
            ".modal-header h4{margin:0;font-size:1.25rem}" +
            ".close-modal{background:none;border:none;font-size:1.8rem;cursor:pointer;color:#6f6257;line-height:1}" +
            ".footer{text-align:center;padding:1.5rem;color:#6f6257;font-size:0.82rem;font-style:italic;border-top:1px solid var(--border);margin-left:260px}" +
            "@media(max-width:768px){.sidebar{transform:translateX(-100%)}.main-content{margin-left:0;padding:1rem}.footer{margin-left:0}}";
    }

    /* ============================ BOOK CATALOG (shared) ============================ */
    static String bookCardHtml(Book b, boolean isAdmin) {
        StringBuilder sb = new StringBuilder();
        String cover = b.coverUrl == null || b.coverUrl.isEmpty() ? "" : "<img src='" + escape(b.coverUrl) + "' alt='' loading='lazy'>";
        sb.append("<div class='book-card'><div class='book-cover'>")
          .append(cover.isEmpty() ? "<div class='no-cover'>" + escape(b.title) + "</div>" : cover)
          .append("</div><div class='book-body'><h4>").append(escape(b.title))
          .append("</h4><p class='author'>").append(escape(b.author)).append("</p>")
          .append("<div class='book-foot'><span class='badge badge-issued'>").append(escape(b.category)).append("</span>")
          .append(b.availableCopies > 0 ? "<span class='badge badge-available'>Available</span>" : "<span class='badge badge-out'>All Out</span>")
          .append("</div><div class='book-foot'><span>").append(b.availableCopies).append(" of ").append(b.totalCopies).append(" copies</span></div>");
        if (isAdmin) {
            sb.append("<div class='book-actions'><button class='btn btn-outline btn-sm' onclick='editBook(")
              .append(b.id).append(",").append(quoteJs(b.title)).append(",").append(quoteJs(b.author))
              .append(",").append(quoteJs(b.isbn)).append(",").append(quoteJs(b.category))
              .append(",").append(b.totalCopies).append(",").append(b.availableCopies)
              .append(")'><i class=\"bi bi-pencil\"></i> Edit</button> ")
              .append("<a href='/books/delete?id=").append(b.id).append("' class='btn btn-danger btn-sm' onclick='return confirmDelete()'><i class=\"bi bi-trash\"></i> Delete</a></div>");
        } else {
            sb.append("<div class='book-actions'>");
            if (b.availableCopies > 0) {
                sb.append("<form method='post' action='/user-borrow'><input type='hidden' name='bookId' value='")
                  .append(b.id).append("'><button class='btn btn-primary btn-sm'><i class=\"bi bi-book\"></i> Borrow</button></form>");
            } else {
                sb.append("<span class='small text-muted'>Check back soon</span>");
            }
            sb.append("</div>");
        }
        sb.append("</div></div>");
        return sb.toString();
    }

    static String bookCatalogHtml(String search, String cat, boolean isAdmin, String error) {
        String q = search == null ? "" : search.toLowerCase();
        List<Book> list = new ArrayList<>();
        for (Book b : books) {
            boolean match = q.isEmpty() || b.title.toLowerCase().contains(q) || b.author.toLowerCase().contains(q)
                || b.category.toLowerCase().contains(q) || b.isbn.contains(search);
            if (match && (cat == null || cat.isEmpty() || b.category.equalsIgnoreCase(cat))) list.add(b);
        }
        Set<String> unique = new TreeSet<>();
        for (Book b : books) unique.add(b.category);
        String base = isAdmin ? "/books" : "/user-books";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='page-flex'><h3><i class='bi bi-book'></i> ").append(isAdmin ? "Book Management" : "Browse the Shelves").append("</h3>");
        if (isAdmin) sb.append("<button class='btn btn-primary' onclick='openBookModal()'><i class=\"bi bi-plus-lg\"></i> Add Book</button>");
        sb.append("</div>");
        if (error != null && !error.isEmpty()) sb.append("<div class='alert alert-danger'>").append(escape(error)).append("</div>");
        sb.append("<form class='search-bar' method='get' action='").append(base).append("'>")
          .append("<input type='text' name='q' placeholder='Search by title, author, category or ISBN…' value='").append(escape(search == null ? "" : search)).append("'>")
          .append("<button class='btn btn-primary'><i class=\"bi bi-search\"></i> Search</button>")
          .append("</form><div class='filter-tabs'><a class='tab ").append(cat == null || cat.isEmpty() ? "tab-active" : "")
          .append("' href='").append(base).append("'>All</a>");
        for (String c : unique)
            sb.append("<a class='tab ").append(c.equalsIgnoreCase(cat) ? "tab-active" : "").append("' href='").append(base).append("?cat=").append(urlEncode(c)).append("'>").append(escape(c)).append("</a>");
        sb.append("</div><div class='book-grid'>");
        if (list.isEmpty()) sb.append("<p class='empty-state'>No books match your search.</p>");
        for (Book b : list) sb.append(bookCardHtml(b, isAdmin));
        sb.append("</div>");
        return sb.toString();
    }

    /* ============================ HANDLERS ============================ */
    static class RootHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            String body = "<div style='text-align:center;'>" +
                "<h1 style='font-size:2.2rem;margin-bottom:0.5rem;'><i class='bi bi-book-half' style='color:var(--gold);'></i> LibraSys</h1>" +
                "<p style='color:#6f6257;font-style:italic;margin-bottom:2.5rem;'>Choose your portal</p>" +
                "<div style='display:flex;gap:1.2rem;justify-content:center;flex-wrap:wrap;'>" +
                "<a href='/admin/login' class='portal-card portal-admin'><i class='bi bi-person-workspace'></i><strong>Librarian</strong><span>Manage books, members, loans</span></a>" +
                "<a href='/reader/login' class='portal-card portal-reader'><i class='bi bi-book'></i><strong>Reader</strong><span>Browse and borrow books</span></a>" +
                "</div></div>";
            sendHtml(e, page("Welcome", body, false, "", ""));
        }
    }

    static class AdminLoginHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if ("GET".equalsIgnoreCase(e.getRequestMethod())) { sendHtml(e, loginPage(null, "admin")); return; }
            Map<String, String> p = parseForm(e.getRequestBody());
            Member m = null;
            for (Member mm : members)
                if (mm.email.equals(p.get("email")) && mm.password.equals(p.get("password")) && "admin@libra.com".equals(mm.email)) { m = mm; break; }
            if (m != null) {
                e.getResponseHeaders().add("Set-Cookie", "session=" + m.id + "; Path=/; HttpOnly");
                e.getResponseHeaders().add("Set-Cookie", "role=admin; Path=/");
                e.getResponseHeaders().add("Set-Cookie", "name=" + urlEncode(m.name) + "; Path=/");
                sendRedirect(e, "/dashboard");
            } else {
                sendHtml(e, loginPage("Invalid librarian credentials.", "admin"));
            }
        }
    }

    static class ReaderLoginHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if ("GET".equalsIgnoreCase(e.getRequestMethod())) { sendHtml(e, loginPage(null, "user")); return; }
            Map<String, String> p = parseForm(e.getRequestBody());
            Member m = null;
            for (Member mm : members)
                if (mm.email.equals(p.get("email")) && mm.password.equals(p.get("password")) && !"admin@libra.com".equals(mm.email)) { m = mm; break; }
            if (m != null) {
                e.getResponseHeaders().add("Set-Cookie", "session=" + m.id + "; Path=/; HttpOnly");
                e.getResponseHeaders().add("Set-Cookie", "role=user; Path=/");
                e.getResponseHeaders().add("Set-Cookie", "name=" + urlEncode(m.name) + "; Path=/");
                sendRedirect(e, "/user-dashboard");
            } else {
                sendHtml(e, loginPage("Invalid reader credentials.", "user"));
            }
        }
    }

    static String loginPage(String error, String role) {
        boolean isAdmin = "admin".equals(role);
        String title = isAdmin ? "Librarian Portal" : "Reader Portal";
        String subtitle = isAdmin ? "Library management access" : "Browse and borrow books";
        String action = isAdmin ? "/admin/login" : "/reader/login";
        String otherPortal = isAdmin ? "/reader/login" : "/admin/login";
        String otherLabel = isAdmin ? "Are you a reader? Sign in here" : "Are you the librarian? Sign in here";
        String body = "<div style='text-align:center;margin-bottom:1.5rem;'>" +
            "<div class='portal-badge " + (isAdmin ? "badge-admin" : "badge-reader") + "'>" +
            "<i class='bi " + (isAdmin ? "bi-person-workspace" : "bi-book") + "'></i> " + title + "</div>" +
            "<p class='portal-subtitle'>" + subtitle + "</p></div>" +
            "<form action='" + action + "' method='POST'>" +
            (error != null ? "<div class='alert alert-danger'>" + escape(error) + "</div>" : "") +
            "<div class='form-group'><label>Email</label><input type='email' class='form-control' name='email' placeholder='Enter email' required autofocus></div>" +
            "<div class='form-group'><label>Password</label><input type='password' class='form-control' name='password' placeholder='Enter password' required></div>" +
            "<button type='submit' class='btn w-100 portal-btn-" + role + "' style='padding:0.9rem;'>Sign In</button>" +
            "</form>" +
            "<div class='text-center' style='margin-top:1.5rem;'>" +
            "<a href='" + otherPortal + "' class='portal-switch'>" + otherLabel + "</a></div>";
        return page(title, body, false, "", "");
    }

    static class LogoutHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            e.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
            e.getResponseHeaders().add("Set-Cookie", "role=; Path=/; Max-Age=0");
            e.getResponseHeaders().add("Set-Cookie", "name=; Path=/; Max-Age=0");
            sendRedirect(e, "/");
        }
    }

    static String stat(String iconClass, String icon, Object value, String label) {
        return "<div class=\"stat-card\"><div class=\"stat-icon " + iconClass + "\"><i class=\"bi " + icon + "\"></i></div>" +
            "<div class=\"stat-content\"><h3>" + value + "</h3><p>" + label + "</p></div></div>";
    }

    static class DashboardHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e)) { sendRedirect(e, "/login"); return; }
            int tb = books.size(), tm = members.size();
            int ti = (int) issues.stream().filter(i -> "issued".equals(i.status)).count();
            double tf = issues.stream().mapToDouble(i -> i.fine).sum();
            String body = "<div class=\"stats-grid\">" +
                stat("admin-primary", "bi-book", tb, "Total Books") +
                stat("secondary", "bi-people", tm, "Total Members") +
                stat("danger", "bi-journal-bookmark", ti, "Books Issued") +
                stat("warning", "bi-currency-rupee", currency(tf), "Total Fines") +
                "</div><div class=\"table-container\"><h3><i class=\"bi bi-journal-bookmark\"></i> Active Issues</h3>" +
                "<table class=\"table\"><thead><tr><th>Book</th><th>Member</th><th>Due Date</th><th>Fine</th></tr></thead><tbody>";
            for (Issue i : issues) if ("issued".equals(i.status)) {
                body += "<tr><td><strong>" + escape(i.bookTitle) + "</strong></td><td>" + escape(i.memberName) +
                    "</td><td>" + i.dueDate + "</td><td>" + currency(i.fine) + "</td></tr>";
            }
            body += "</tbody></table></div>";
            /* popular books + category breakdown */
            Map<String, Integer> pop = new HashMap<>();
            for (Issue i : issues) pop.merge(i.bookTitle, 1, Integer::sum);
            List<Map.Entry<String, Integer>> top = new ArrayList<>(pop.entrySet());
            top.sort((a, b) -> b.getValue() - a.getValue());
            body += "<div class='two-col'><div class='table-container'><h3><i class='bi bi-fire'></i> Most Borrowed Books</h3><table class='table'><thead><tr><th>Book</th><th>Times Borrowed</th></tr></thead><tbody>";
            if (top.isEmpty()) body += "<tr><td colspan='2' class='empty'>No borrowings yet.</td></tr>";
            for (int k = 0; k < Math.min(5, top.size()); k++)
                body += "<tr><td><strong>" + escape(top.get(k).getKey()) + "</strong></td><td>" + top.get(k).getValue() + "</td></tr>";
            body += "</tbody></table></div>";
            Map<String, Integer> byCat = new java.util.LinkedHashMap<>();
            for (Book b : books) byCat.merge(b.category, 1, Integer::sum);
            body += "<div class='table-container'><h3><i class='bi bi-pie-chart'></i> Collection by Category</h3><table class='table'><thead><tr><th>Category</th><th>Titles</th></tr></thead><tbody>";
            for (Map.Entry<String, Integer> en : byCat.entrySet())
                body += "<tr><td><span class='badge badge-issued'>" + escape(en.getKey()) + "</span></td><td>" + en.getValue() + "</td></tr>";
            body += "</tbody></table></div></div>";
            sendHtml(e, page("Dashboard", body, true, "admin", "/dashboard"));
        }
    }

    static class BooksHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e)) { sendRedirect(e, "/login"); return; }
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) { handleBookPost(e); return; }
            if (e.getRequestURI().getPath().contains("/delete")) {
                int id = Integer.parseInt(e.getRequestURI().getQuery().split("id=")[1]);
                books.removeIf(b -> b.id == id);
                sendRedirect(e, "/books"); return;
            }
            String body = bookCatalogHtml(getParam(e, "q"), getParam(e, "cat"), true, getParam(e, "error")) + bookModal();
            sendHtml(e, page("Books", body, true, "admin", "/books"));
        }
        void handleBookPost(HttpExchange e) throws IOException {
            Map<String, String> p = parseForm(e.getRequestBody());
            String action = p.get("action");
            if ("add".equals(action)) {
                int tc = Integer.parseInt(p.get("totalCopies"));
                addBook(nextBookId++, p.get("title"), p.get("author"), p.get("isbn"), p.get("category"), tc, tc);
            } else if ("update".equals(action)) {
                int id = Integer.parseInt(p.get("id"));
                for (Book b : books) if (b.id == id) {
                    b.title = p.get("title"); b.author = p.get("author"); b.isbn = p.get("isbn"); b.category = p.get("category");
                    b.totalCopies = Integer.parseInt(p.get("totalCopies")); b.availableCopies = Integer.parseInt(p.get("availableCopies"));
                    b.coverUrl = "https://covers.openlibrary.org/b/isbn/" + b.isbn.replace("-", "") + "-M.jpg";
                    break;
                }
            }
            sendRedirect(e, "/books");
        }
        String bookModal() {
            return "<div class=\"modal\" id=\"bookModal\"><div class=\"modal-content\">" +
                "<div class=\"modal-header\"><h4 id=\"bookModalTitle\">Add New Book</h4><button class=\"close-modal\" onclick=\"closeModal('bookModal')\">&times;</button></div>" +
                "<form action=\"/books\" method=\"POST\">" +
                "<input type=\"hidden\" name=\"action\" id=\"bookAction\" value=\"add\">" +
                "<input type=\"hidden\" name=\"id\" id=\"bookId\">" +
                "<div class=\"form-group\"><label>Book Title</label><input type=\"text\" class=\"form-control\" name=\"title\" id=\"bookTitle\" required></div>" +
                "<div class=\"form-group\"><label>Author</label><input type=\"text\" class=\"form-control\" name=\"author\" id=\"bookAuthor\" required></div>" +
                "<div class=\"form-group\"><label>ISBN</label><input type=\"text\" class=\"form-control\" name=\"isbn\" id=\"bookIsbn\"></div>" +
                "<div class=\"form-group\"><label>Category</label><input type=\"text\" class=\"form-control\" name=\"category\" id=\"bookCategory\"></div>" +
                "<div class=\"row\"><div class=\"col-md-6\"><div class=\"form-group\"><label>Total Copies</label><input type=\"number\" class=\"form-control\" name=\"totalCopies\" id=\"bookTotalCopies\" min=\"1\" required></div></div>" +
                "<div class=\"col-md-6\"><div class=\"form-group\"><label>Available Copies</label><input type=\"number\" class=\"form-control\" name=\"availableCopies\" id=\"bookAvailableCopies\" min=\"0\" required></div></div></div>" +
                "<div class=\"d-flex gap-2 mt-3\"><button type=\"button\" class=\"btn btn-outline\" onclick=\"closeModal('bookModal')\">Cancel</button><button type=\"submit\" class=\"btn btn-primary\">Save Book</button></div>" +
                "</form></div></div>" +
                "<script>function openBookModal(){document.getElementById('bookModalTitle').textContent='Add New Book';" +
                "document.getElementById('bookAction').value='add';document.getElementById('bookId').value='';" +
                "['bookTitle','bookAuthor','bookIsbn','bookCategory','bookTotalCopies','bookAvailableCopies'].forEach(i=>document.getElementById(i).value='');" +
                "document.getElementById('bookModal').classList.add('show');}" +
                "function editBook(id,t,a,i,c,tc,ac){document.getElementById('bookModalTitle').textContent='Edit Book';" +
                "document.getElementById('bookAction').value='update';document.getElementById('bookId').value=id;" +
                "document.getElementById('bookTitle').value=t;document.getElementById('bookAuthor').value=a;" +
                "document.getElementById('bookIsbn').value=i;document.getElementById('bookCategory').value=c;" +
                "document.getElementById('bookTotalCopies').value=tc;document.getElementById('bookAvailableCopies').value=ac;" +
                "document.getElementById('bookModal').classList.add('show');}</script>";
        }
    }

    static class MembersHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e)) { sendRedirect(e, "/login"); return; }
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) { handleMemberPost(e); return; }
            if (e.getRequestURI().getPath().contains("/delete")) {
                int id = Integer.parseInt(e.getRequestURI().getQuery().split("id=")[1]);
                members.removeIf(m -> m.id == id);
                sendRedirect(e, "/members"); return;
            }
            showMembersPage(e);
        }
        void handleMemberPost(HttpExchange e) throws IOException {
            Map<String, String> p = parseForm(e.getRequestBody());
            String action = p.get("action");
            if ("add".equals(action)) {
                addMember(nextMemberId++, p.get("name"), p.get("email"), p.get("password"), p.get("phone"), p.get("address"), p.get("joinDate"), p.get("status"));
            } else if ("update".equals(action)) {
                int id = Integer.parseInt(p.get("id"));
                for (Member m : members) if (m.id == id) {
                    m.name = p.get("name"); m.email = p.get("email"); m.password = p.get("password");
                    m.phone = p.get("phone"); m.address = p.get("address"); m.joinDate = p.get("joinDate"); m.status = p.get("status");
                    break;
                }
            }
            sendRedirect(e, "/members");
        }
        void showMembersPage(HttpExchange e) throws IOException {
            StringBuilder rows = new StringBuilder(); int n = 1;
            for (Member m : members) {
                rows.append("<tr><td>").append(n++).append("</td><td><strong>").append(escape(m.name))
                    .append("</strong></td><td>").append(escape(m.email)).append("</td><td>").append(escape(m.phone))
                    .append("</td><td>").append(m.joinDate).append("</td><td><span class=\"badge badge-").append(m.status)
                    .append("\">").append(m.status.toUpperCase()).append("</span></td><td>")
                    .append("<button class=\"btn btn-outline btn-sm\" onclick=\"editMember(").append(m.id).append(",")
                    .append(quoteJs(m.name)).append(",").append(quoteJs(m.email)).append(",").append(quoteJs(m.password))
                    .append(",").append(quoteJs(m.phone)).append(",").append(quoteJs(m.address)).append(",'")
                    .append(m.joinDate).append("','").append(m.status).append(")'><i class=\"bi bi-pencil\"></i></button> ")
                    .append("<a href=\"/members/delete?id=").append(m.id).append("\" class=\"btn btn-danger btn-sm\" onclick=\"return confirmDelete()\"><i class=\"bi bi-trash\"></i></a>")
                    .append("</td></tr>");
            }
            String body = "<div class=\"page-flex\"><h3><i class=\"bi bi-people\"></i> Member Management</h3>" +
                "<button class=\"btn btn-primary\" onclick=\"openMemberModal()\"><i class=\"bi bi-plus-lg\"></i> Add Member</button></div>" +
                "<div class=\"table-container\"><table class=\"table\"><thead><tr><th>#</th><th>Name</th><th>Email</th><th>Phone</th><th>Join Date</th><th>Status</th><th>Actions</th></tr></thead><tbody>"
                + rows + "</tbody></table></div>" + memberModal();
            sendHtml(e, page("Members", body, true, "admin", "/members"));
        }
        String memberModal() {
            return "<div class=\"modal\" id=\"memberModal\"><div class=\"modal-content\">" +
                "<div class=\"modal-header\"><h4 id=\"memberModalTitle\">Add New Member</h4><button class=\"close-modal\" onclick=\"closeModal('memberModal')\">&times;</button></div>" +
                "<form action=\"/members\" method=\"POST\">" +
                "<input type=\"hidden\" name=\"action\" id=\"memberAction\" value=\"add\">" +
                "<input type=\"hidden\" name=\"id\" id=\"memberId\">" +
                "<div class=\"form-group\"><label>Full Name</label><input type=\"text\" class=\"form-control\" name=\"name\" id=\"memberName\" required></div>" +
                "<div class=\"form-group\"><label>Email</label><input type=\"email\" class=\"form-control\" name=\"email\" id=\"memberEmail\" required></div>" +
                "<div class=\"form-group\"><label>Password</label><input type=\"password\" class=\"form-control\" name=\"password\" id=\"memberPassword\" required></div>" +
                "<div class=\"form-group\"><label>Phone</label><input type=\"text\" class=\"form-control\" name=\"phone\" id=\"memberPhone\"></div>" +
                "<div class=\"form-group\"><label>Address</label><input type=\"text\" class=\"form-control\" name=\"address\" id=\"memberAddress\"></div>" +
                "<div class=\"row\"><div class=\"col-md-6\"><div class=\"form-group\"><label>Join Date</label><input type=\"date\" class=\"form-control\" name=\"joinDate\" id=\"memberJoinDate\" required></div></div>" +
                "<div class=\"col-md-6\"><div class=\"form-group\"><label>Status</label><select class=\"form-control\" name=\"status\" id=\"memberStatus\"><option>active</option><option>inactive</option><option>blocked</option></select></div></div></div>" +
                "<div class=\"d-flex gap-2 mt-3\"><button type=\"button\" class=\"btn btn-outline\" onclick=\"closeModal('memberModal')\">Cancel</button><button type=\"submit\" class=\"btn btn-primary\">Save Member</button></div>" +
                "</form></div></div>" +
                "<script>function openMemberModal(){document.getElementById('memberModalTitle').textContent='Add New Member';" +
                "document.getElementById('memberAction').value='add';document.getElementById('memberId').value='';" +
                "['memberName','memberEmail','memberPassword','memberPhone','memberAddress','memberJoinDate'].forEach(i=>document.getElementById(i).value='');" +
                "document.getElementById('memberStatus').value='active';document.getElementById('memberModal').classList.add('show');}" +
                "function editMember(id,n,e,p,ph,a,jd,st){document.getElementById('memberModalTitle').textContent='Edit Member';" +
                "document.getElementById('memberAction').value='update';document.getElementById('memberId').value=id;" +
                "document.getElementById('memberName').value=n;document.getElementById('memberEmail').value=e;" +
                "document.getElementById('memberPassword').value=p;document.getElementById('memberPhone').value=ph;" +
                "document.getElementById('memberAddress').value=a;document.getElementById('memberJoinDate').value=jd;" +
                "document.getElementById('memberStatus').value=st;document.getElementById('memberModal').classList.add('show');}</script>";
        }
    }

    static class IssuesHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e)) { sendRedirect(e, "/login"); return; }
            if ("POST".equalsIgnoreCase(e.getRequestMethod())) { handleIssuePost(e); return; }
            if (e.getRequestURI().getPath().contains("/return")) {
                int id = Integer.parseInt(e.getRequestURI().getQuery().split("id=")[1]);
                returnBook(id);
                sendRedirect(e, "/issues"); return;
            }
            showIssuesPage(e);
        }
        void handleIssuePost(HttpExchange e) throws IOException {
            Map<String, String> p = parseForm(e.getRequestBody());
            int bookId = Integer.parseInt(p.get("bookId")), memberId = Integer.parseInt(p.get("memberId"));
            Book book = findBook(bookId); Member member = findMember(memberId);
            if (book != null && member != null && book.availableCopies > 0) {
                book.availableCopies--;
                addIssue(nextIssueId++, bookId, memberId, book.title, member.name, p.get("issueDate"), p.get("dueDate"), null, 0.0, "issued");
            }
            sendRedirect(e, "/issues");
        }
        void returnBook(int id) {
            for (Issue i : issues) if (i.id == id && "issued".equals(i.status)) {
                i.status = "returned";
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                i.returnDate = sdf.format(new Date());
                long diff = 0;
                try { diff = (new Date().getTime() - sdf.parse(i.dueDate).getTime()) / (1000 * 60 * 60 * 24); } catch (Exception ignored) {}
                if (diff > 0) i.fine = diff * 10.0;
                for (Book b : books) if (b.id == i.bookId) { b.availableCopies++; break; }
                break;
            }
        }
        void showIssuesPage(HttpExchange e) throws IOException {
            StringBuilder rows = new StringBuilder(); int n = 1;
            for (Issue i : issues) {
                rows.append("<tr><td>").append(n++).append("</td><td><strong>").append(escape(i.bookTitle))
                    .append("</strong></td><td>").append(escape(i.memberName)).append("</td><td>").append(i.issueDate)
                    .append("</td><td>").append(i.dueDate).append("</td><td>")
                    .append(i.returnDate != null ? i.returnDate : "-").append("</td><td>").append(currency(i.fine))
                    .append("</td><td><span class=\"badge badge-").append(i.status).append("\">").append(i.status.toUpperCase())
                    .append("</span></td><td>");
                if ("issued".equals(i.status)) {
                    rows.append("<a href=\"/issues/return?id=").append(i.id).append("\" class=\"btn btn-success btn-sm\" onclick=\"return confirmDelete()\"><i class=\"bi bi-check2\"></i> Return</a>");
                }
                rows.append("</td></tr>");
            }
            StringBuilder bookOpts = new StringBuilder();
            for (Book b : books) if (b.availableCopies > 0)
                bookOpts.append("<option value=\"").append(b.id).append("\">").append(escape(b.title)).append(" (Available: ").append(b.availableCopies).append(")</option>");
            StringBuilder memberOpts = new StringBuilder();
            for (Member m : members)
                memberOpts.append("<option value=\"").append(m.id).append("\">").append(escape(m.name)).append("</option>");
            String body = "<h3 class=\"mb-3\" style=\"font-family:'Fraunces',Georgia,serif;\"><i class=\"bi bi-journal-bookmark\" style=\"color:var(--gold);\"></i> Issue & Return Books</h3>" +
                "<div class=\"table-container\"><table class=\"table\"><thead><tr><th>#</th><th>Book</th><th>Member</th><th>Issue Date</th><th>Due Date</th><th>Return Date</th><th>Fine</th><th>Status</th><th>Action</th></tr></thead><tbody>"
                + rows + "</tbody></table></div>" +
                "<div class=\"form-card\"><h3 style=\"margin-bottom:1rem;\"><i class=\"bi bi-plus-lg\" style=\"color:var(--gold);\"></i> Issue New Book</h3>" +
                "<form action=\"/issues\" method=\"POST\"><div class=\"row\">" +
                "<div class=\"col-md-6\"><div class=\"form-group\"><label>Book</label><select class=\"form-control\" name=\"bookId\">" + bookOpts + "</select></div></div>" +
                "<div class=\"col-md-6\"><div class=\"form-group\"><label>Member</label><select class=\"form-control\" name=\"memberId\">" + memberOpts + "</select></div></div>" +
                "<div class=\"col-md-6\"><div class=\"form-group\"><label>Issue Date</label><input type=\"date\" class=\"form-control\" name=\"issueDate\" required></div></div>" +
                "<div class=\"col-md-6\"><div class=\"form-group\"><label>Due Date</label><input type=\"date\" class=\"form-control\" name=\"dueDate\" required></div></div>" +
                "</div><button type=\"submit\" class=\"btn btn-primary mt-2\">Issue Book</button></form></div>";
            sendHtml(e, page("Issues", body, true, "admin", "/issues"));
        }
    }

    static class UserDashboardHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e)) { sendRedirect(e, "/login"); return; }
            if (!"user".equals(getCookie(e, "role"))) { sendRedirect(e, "/dashboard"); return; }
            Member m = findMemberByName(getCookie(e, "name"));
            int mid = m != null ? m.id : -1;
            int myIssues = (int) issues.stream().filter(i -> i.memberId == mid && "issued".equals(i.status)).count();
            double myFines = issues.stream().filter(i -> i.memberId == mid).mapToDouble(i -> i.fine).sum();
            String body = "<div class='welcome-banner'><div><h2 class='serif'>Hi, " + escape(m != null ? m.name.split(" ")[0] : "Reader") + " 👋</h2>" +
                "<p>Here's what's on your shelf right now.</p></div></div>" +
                "<div class=\"stats-grid\">" +
                stat("user-primary", "bi-book", myIssues, "My Issued Books") +
                stat("warning", "bi-currency-rupee", currency(myFines), "My Fines") +
                "</div><div class=\"table-container\"><h3><i class=\"bi bi-journal-bookmark\"></i> My Issues</h3>" +
                "<table class=\"table\"><thead><tr><th>Book</th><th>Issue Date</th><th>Due Date</th><th>Status</th><th>Fine</th></tr></thead><tbody>";
            for (Issue i : issues) if (m != null && i.memberId == m.id) {
                String dueBadge;
                if (!"issued".equals(i.status)) dueBadge = "";
                else {
                    long daysLeft = 0;
                    try { daysLeft = (new java.text.SimpleDateFormat("yyyy-MM-dd").parse(i.dueDate).getTime() - System.currentTimeMillis()) / 86400000L; } catch (Exception ignored) {}
                    dueBadge = daysLeft < 0 ? " <span class='badge badge-blocked'>Overdue</span>"
                        : daysLeft <= 3 ? " <span class='badge badge-issued'>Due in " + daysLeft + "d</span>" : "";
                }
                body += "<tr><td><strong>" + escape(i.bookTitle) + "</strong>" + dueBadge + "</td><td>" + i.issueDate +
                    "</td><td>" + i.dueDate + "</td><td><span class=\"badge badge-" + i.status + "\">" + i.status.toUpperCase() + "</span></td><td>" + currency(i.fine) + "</td></tr>";
            }
            body += "</tbody></table></div>";
            /* recommendations: available books not borrowed by me */
            body += "<div class='table-container'><h3><i class='bi bi-stars'></i> You Might Also Like</h3><div style='padding:1.2rem;display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:1rem;'>";
            int shown = 0;
            for (Book b : books) {
                if (b.availableCopies <= 0) continue;
                if (m != null && issues.stream().anyMatch(i -> i.memberId == m.id && i.bookId == b.id && "issued".equals(i.status))) continue;
                if (shown >= 4) break;
                shown++;
                body += "<div class='rec-card'><div class='book-cover' style='height:130px;'>" +
                    (b.coverUrl == null || b.coverUrl.isEmpty() ? "<div class='no-cover' style='font-size:0.8rem;'>" + escape(b.title) + "</div>" : "<img src='" + escape(b.coverUrl) + "'>") +
                    "</div><div style='padding:0.6rem 0.7rem;'><div style='font-size:0.85rem;font-weight:600;line-height:1.2;'>" + escape(b.title) + "</div>" +
                    "<div style='font-size:0.75rem;color:#6f6257;'>" + escape(b.author) + "</div></div>" +
                    "<form method='post' action='/user-borrow'><input type='hidden' name='bookId' value='" + b.id + "'><button class='btn btn-primary btn-sm' style='width:100%;border-radius:0 0 14px 14px;'>Borrow</button></form></div>";
            }
            body += "</div></div>";
            sendHtml(e, page("My Dashboard", body, true, "user", "/user-dashboard"));
        }
    }

    static class UserBooksHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e)) { sendRedirect(e, "/login"); return; }
            if (!"user".equals(getCookie(e, "role"))) { sendRedirect(e, "/books"); return; }
            String body = bookCatalogHtml(getParam(e, "q"), getParam(e, "cat"), false, getParam(e, "error"));
            sendHtml(e, page("Browse the Shelves", body, true, "user", "/user-books"));
        }
    }

    static class UserBorrowHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e) || !"user".equals(getCookie(e, "role"))) { sendRedirect(e, "/login"); return; }
            Map<String, String> p = parseForm(e.getRequestBody());
            int bookId = Integer.parseInt(p.get("bookId"));
            Member me = findMemberByName(getCookie(e, "name"));
            Book book = findBook(bookId);
            if (book == null || me == null) { sendRedirect(e, "/user-books"); return; }
            if (book.availableCopies <= 0) {
                sendRedirect(e, "/user-books?error=" + urlEncode("Sorry, no copies of \"" + book.title + "\" are available right now.")); return;
            }
            long active = issues.stream().filter(i -> i.memberId == me.id && "issued".equals(i.status)).count();
            if (active >= 5) {
                sendRedirect(e, "/user-books?error=" + urlEncode("You have reached the maximum of 5 books on loan.")); return;
            }
            boolean already = issues.stream().anyMatch(i -> i.memberId == me.id && i.bookId == bookId && "issued".equals(i.status));
            if (already) {
                sendRedirect(e, "/user-books?error=" + urlEncode("You already have \"" + book.title + "\" on loan.")); return;
            }
            Calendar cal = Calendar.getInstance();
            String issueDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 14);
            String dueDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
            book.availableCopies--;
            addIssue(nextIssueId++, bookId, me.id, book.title, me.name, issueDate, dueDate, null, 0.0, "issued");
            sendRedirect(e, "/user-issues");
        }
    }

    static class UserIssuesHandler implements HttpHandler {
        public void handle(HttpExchange e) throws IOException {
            CURRENT.set(e);
            if (!isLoggedIn(e)) { sendRedirect(e, "/login"); return; }
            if (!"user".equals(getCookie(e, "role"))) { sendRedirect(e, "/issues"); return; }
            Member m = findMemberByName(getCookie(e, "name"));
            StringBuilder rows = new StringBuilder(); int n = 1;
            for (Issue i : issues) if (m != null && i.memberId == m.id) {
                rows.append("<tr><td>").append(n++).append("</td><td><strong>").append(escape(i.bookTitle))
                    .append("</strong></td><td>").append(i.issueDate).append("</td><td>").append(i.dueDate)
                    .append("</td><td>").append(i.returnDate != null ? i.returnDate : "-").append("</td><td>")
                    .append(currency(i.fine)).append("</td><td><span class=\"badge badge-").append(i.status)
                    .append("\">").append(i.status.toUpperCase()).append("</span></td></tr>");
            }
            String body = "<div class=\"table-container\"><h3><i class=\"bi bi-journal-bookmark\"></i> My Issue History</h3>" +
                "<table class=\"table\"><thead><tr><th>#</th><th>Book</th><th>Issue Date</th><th>Due Date</th><th>Return Date</th><th>Fine</th><th>Status</th></tr></thead><tbody>"
                + rows + "</tbody></table></div>";
            sendHtml(e, page("My Issues", body, true, "user", "/user-issues"));
        }
    }

    /* ============================ MAIN ============================ */
    public static void main(String[] args) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(8080), 0);
        s.createContext("/", new RootHandler());
        s.createContext("/admin/login", new AdminLoginHandler());
        s.createContext("/reader/login", new ReaderLoginHandler());
        s.createContext("/login", new RootHandler());
        s.createContext("/logout", new LogoutHandler());
        s.createContext("/dashboard", new DashboardHandler());
        s.createContext("/books", new BooksHandler());
        s.createContext("/members", new MembersHandler());
        s.createContext("/issues", new IssuesHandler());
        s.createContext("/user-dashboard", new UserDashboardHandler());
        s.createContext("/user-books", new UserBooksHandler());
        s.createContext("/user-borrow", new UserBorrowHandler());
        s.createContext("/user-issues", new UserIssuesHandler());
        s.setExecutor(Executors.newFixedThreadPool(10));
        s.start();
        System.out.println("================================================");
        System.out.println("  LibraSys Library Management System");
        System.out.println("  Server: http://localhost:8080");
        System.out.println("  Admin: admin@libra.com / admin123");
        System.out.println("  User:  john@email.com / john123");
        System.out.println("================================================");
    }
}
