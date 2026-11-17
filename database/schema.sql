-- =====================================================================
-- BorrowBuddy - MySQL schema
-- ---------------------------------------------------------------------
-- This file mirrors the JPA entities. The application normally creates
-- and updates these tables automatically (spring.jpa.hibernate.ddl-auto),
-- so running this script is optional. It is provided for:
--   * MySQL Workbench users who prefer to create the schema manually
--   * reviewers who want to inspect the data model
--   * fresh environments where DDL auto-update is disabled
--
-- Usage (MySQL Workbench or mysql CLI):
--   CREATE DATABASE IF NOT EXISTS borrowbuddy_db
--     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   USE borrowbuddy_db;
--   SOURCE database/schema.sql;
-- =====================================================================

CREATE DATABASE IF NOT EXISTS borrowbuddy_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE borrowbuddy_db;

-- ---------------------------------------------------------------------
-- students
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(120) NOT NULL,
  college_email VARCHAR(150) NOT NULL,
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt hash - never plain text',
  student_id    VARCHAR(50)  NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
  profile_image VARCHAR(500) NULL,
  created_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_students_email (college_email),
  UNIQUE KEY uk_students_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- items (soft-deleted with is_deleted so history is preserved)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS items (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  item_name           VARCHAR(150)  NOT NULL,
  category            VARCHAR(60)   NOT NULL,
  description         VARCHAR(2000) NOT NULL,
  image_url           VARCHAR(500)  NULL,
  item_condition      VARCHAR(20)   NOT NULL,
  availability_status VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
  price_per_day       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Rental price per day in INR; 0 = free',
  security_deposit    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Refundable deposit in INR; 0 = none',
  contact_phone       VARCHAR(20)   NULL COMMENT 'Optional contact phone shown to signed-in students',
  owner_id            BIGINT        NOT NULL,
  is_deleted          TINYINT(1)    NOT NULL DEFAULT 0,
  created_at          DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_items_owner (owner_id),
  KEY idx_items_category (category),
  KEY idx_items_available (availability_status, is_deleted),
  CONSTRAINT fk_items_owner FOREIGN KEY (owner_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- borrow_requests
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS borrow_requests (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  item_id      BIGINT        NOT NULL,
  borrower_id  BIGINT        NOT NULL,
  owner_id     BIGINT        NOT NULL,
  request_date DATE          NOT NULL,
  start_date   DATE          NOT NULL,
  return_date  DATE          NOT NULL,
  message      VARCHAR(1000) NULL,
  status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  created_at   DATETIME(6)   NOT NULL,
  updated_at   DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_requests_item (item_id),
  KEY idx_requests_borrower (borrower_id),
  KEY idx_requests_owner (owner_id),
  KEY idx_requests_status (status),
  KEY idx_requests_dates (item_id, status, start_date, return_date),
  CONSTRAINT fk_requests_item FOREIGN KEY (item_id) REFERENCES items (id),
  CONSTRAINT fk_requests_borrower FOREIGN KEY (borrower_id) REFERENCES students (id),
  CONSTRAINT fk_requests_owner FOREIGN KEY (owner_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- transactions (one per approved request)
-- qr_token_hash stores only the SHA-256 hash of the single-use token.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
  id                  BIGINT      NOT NULL AUTO_INCREMENT,
  request_id          BIGINT      NOT NULL,
  handover_date       DATETIME(6) NULL,
  actual_return_date  DATETIME(6) NULL,
  status              VARCHAR(30) NOT NULL DEFAULT 'AWAITING_HANDOVER',
  qr_token_hash       VARCHAR(64) NULL,
  qr_token_expires_at DATETIME(6) NULL,
  created_at          DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_transactions_request (request_id),
  UNIQUE KEY uk_transactions_qr (qr_token_hash),
  KEY idx_transactions_status (status),
  CONSTRAINT fk_transactions_request FOREIGN KEY (request_id) REFERENCES borrow_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- reviews (one review per reviewer per transaction)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  reviewer_id        BIGINT        NOT NULL,
  reviewed_student_id BIGINT       NOT NULL,
  transaction_id     BIGINT        NOT NULL,
  rating             INT           NOT NULL,
  comment            VARCHAR(1000) NULL,
  created_at         DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reviews_reviewer_transaction (reviewer_id, transaction_id),
  KEY idx_reviews_reviewed (reviewed_student_id),
  KEY idx_reviews_transaction (transaction_id),
  CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES students (id),
  CONSTRAINT fk_reviews_reviewed FOREIGN KEY (reviewed_student_id) REFERENCES students (id),
  CONSTRAINT fk_reviews_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
  CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- notifications
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  student_id        BIGINT       NOT NULL,
  message           VARCHAR(500) NOT NULL,
  notification_type VARCHAR(30)  NOT NULL,
  is_read           TINYINT(1)   NOT NULL DEFAULT 0,
  reference_id      BIGINT       NULL,
  link              VARCHAR(200) NULL,
  created_at        DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_notifications_student (student_id),
  KEY idx_notifications_unread (student_id, is_read),
  CONSTRAINT fk_notifications_student FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- reports (moderation queue)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  reporter_id BIGINT        NOT NULL,
  target_type VARCHAR(20)   NOT NULL,
  target_id   BIGINT        NOT NULL,
  reason      VARCHAR(1000) NOT NULL,
  status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
  created_at  DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_reports_reporter (reporter_id),
  KEY idx_reports_status (status),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- stored_images (uploaded and demo photos, served from /uploads/<name>)
-- LONGBLOB is required: the default BLOB/TINYBLOB is too small for photos.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stored_images (
  filename     VARCHAR(80) NOT NULL,
  content_type VARCHAR(60) NOT NULL,
  byte_size    INT         NOT NULL,
  sha256       VARCHAR(64) NOT NULL,
  data         LONGBLOB    NOT NULL,
  created_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (filename)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
