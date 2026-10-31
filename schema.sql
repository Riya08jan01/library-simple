-- ============================================================
-- LibraSys — Library Management System
-- Database Schema (SQL)
-- ============================================================
-- Compatible with MySQL, PostgreSQL, SQLite, and most SQL databases.
-- For SQLite, remove AUTO_INCREMENT / SERIAL and use INTEGER PRIMARY KEY instead.
-- ============================================================

-- -----------------------------------------------------
-- Table: books
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS books (
    id          INTEGER PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    author      VARCHAR(255) NOT NULL,
    isbn        VARCHAR(50),
    category    VARCHAR(100),
    total_copies INTEGER DEFAULT 1,
    available_copies INTEGER DEFAULT 1,
    cover_url   VARCHAR(500)
);

-- -----------------------------------------------------
-- Table: members
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS members (
    id         INTEGER PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    phone      VARCHAR(50),
    address    VARCHAR(500),
    join_date  DATE,
    status     VARCHAR(50) DEFAULT 'active'
);

-- -----------------------------------------------------
-- Table: issues
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS issues (
    id          INTEGER PRIMARY KEY,
    book_id     INTEGER,
    member_id   INTEGER,
    book_title  VARCHAR(255),
    member_name VARCHAR(255),
    issue_date  DATE,
    due_date    DATE,
    return_date DATE,
    fine        DECIMAL(10,2) DEFAULT 0.00,
    status      VARCHAR(50) DEFAULT 'issued',
    FOREIGN KEY (book_id)  REFERENCES books(id),
    FOREIGN KEY (member_id) REFERENCES members(id)
);

-- -----------------------------------------------------
-- Seed Data: Books (matches LibraryServer.java static block)
-- -----------------------------------------------------
INSERT INTO books (id, title, author, isbn, category, total_copies, available_copies, cover_url) VALUES
(1,  'The Great Gatsby',               'F. Scott Fitzgerald',              '978-0743273565', 'Fiction',                3, 3, NULL),
(2,  'To Kill a Mockingbird',          'Harper Lee',                       '978-0061120084', 'Fiction',                2, 2, NULL),
(3,  '1984',                           'George Orwell',                    '978-0451524935', 'Science Fiction',        4, 4, NULL),
(4,  'Clean Code',                     'Robert C. Martin',                 '978-0132350884', 'Technology',             3, 3, NULL),
(5,  'Atomic Habits',                  'James Clear',                      '978-0735211292', 'Self Help',              5, 5, NULL),
(6,  'Sapiens',                        'Yuval Noah Harari',                '978-0062316097', 'History',                3, 3, NULL),
(7,  'The Hobbit',                     'J.R.R. Tolkien',                   '978-0547928227', 'Fantasy',                3, 3, NULL),
(8,  'A Brief History of Time',        'Stephen Hawking',                  '978-0553380163', 'Science',                2, 2, NULL),
(9,  'Harry Potter and the Sorcerer\'s Stone', 'J.K. Rowling',             '978-0439708184', 'Fantasy',                4, 4, NULL),
(10, 'The Alchemist',                  'Paulo Coelho',                     '978-0062315007', 'Fiction',                3, 3, NULL),
(11, 'Head First Java',                'Kathy Sierra',                     '978-1491910771', 'Technology',             2, 2, NULL),
(12, 'Pride and Prejudice',            'Jane Austen',                      '978-0141439518', 'Fiction',                2, 2, NULL),
(13, 'The Diary of a Young Girl',      'Anne Frank',                       '978-0553296983', 'History',                2, 2, NULL),
(14, 'Introduction to Algorithms',     'Thomas H. Cormen',                 '978-0262046305', 'Technology',             2, 2, NULL),
(15, 'Rich Dad Poor Dad',              'Robert T. Kiyosaki',               '978-1612680194', 'Self Help',              3, 3, NULL),
(16, 'Cosmos',                         'Carl Sagan',                       '978-0345539434', 'Science',                2, 2, NULL);

-- -----------------------------------------------------
-- Seed Data: Members
-- -----------------------------------------------------
INSERT INTO members (id, name, email, password, phone, address, join_date, status) VALUES
(1,  'John Doe',     'john@email.com',   'john123',   '555-1234', '123 Main Street',           '2024-01-15', 'active'),
(2,  'Jane Smith',   'jane@email.com',   'jane123',   '555-5678', '456 Oak Avenue',            '2024-02-20', 'active'),
(3,  'Admin User',   'admin@libra.com',  'admin123',  '555-0000', 'Library HQ',                '2023-01-01', 'active');

-- -----------------------------------------------------
-- Seed Data: Issues
-- -----------------------------------------------------
INSERT INTO issues (id, book_id, member_id, book_title, member_name, issue_date, due_date, return_date, fine, status) VALUES
(1,  1,  1, 'The Great Gatsby', 'John Doe', '2024-10-01', '2024-10-15', NULL, 0.00, 'issued');

-- Update available copies for the seeded issue
UPDATE books SET available_copies = available_copies - 1 WHERE id = 1;