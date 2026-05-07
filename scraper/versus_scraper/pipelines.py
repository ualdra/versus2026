import hashlib
import logging
import os
from datetime import datetime, timezone

import psycopg2
from itemadapter import ItemAdapter

from versus_scraper.items import QuestionItem

logger = logging.getLogger(__name__)


class DeerdaysScraperPipeline:
    """Inserts scraped QuestionItems into PostgreSQL.

    Non-QuestionItem items are passed through unchanged so other pipelines
    (e.g. JSON export) can still consume them.
    """

    def open_spider(self, spider):
        self.conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "localhost"),
            port=int(os.getenv("DB_PORT", "5432")),
            dbname=os.getenv("DB_NAME", "versus"),
            user=os.getenv("DB_USER", "versus"),
            password=os.getenv("DB_PASSWORD", "versus"),
        )
        self.conn.autocommit = False
        self.cursor = self.conn.cursor()

        self.questions_inserted = 0
        self.errors = 0
        self.run_id = None
        self.spider_id = self._resolve_spider_id(spider.name)

        if self.spider_id:
            self._start_run()

    def close_spider(self, spider):
        try:
            if self.run_id:
                self._finish_run()
            self.conn.commit()
        except Exception:
            self.conn.rollback()
            logger.exception("Error finalizing spider run")
        finally:
            self.cursor.close()
            self.conn.close()

    def process_item(self, item, spider):
        if not isinstance(item, QuestionItem):
            return item

        adapter = ItemAdapter(item)
        text = adapter.get("text", "").strip()
        if not text:
            self.errors += 1
            return item

        question_type = adapter.get("type", "BINARY")
        if not self._is_valid(adapter, question_type):
            self.errors += 1
            return item

        text_hash = hashlib.sha256(text.encode()).hexdigest()

        try:
            question_id = self._upsert_question(adapter, text, text_hash, question_type)
            if question_id and question_type == "BINARY":
                self._upsert_options(question_id, adapter.get("options", []))
            if question_id:
                self.questions_inserted += 1
            self.conn.commit()
        except Exception:
            self.conn.rollback()
            self.errors += 1
            logger.exception("Error inserting question: %s", text[:60])

        return item

    # ── Internal helpers ──────────────────────────────────────────────────────

    def _is_valid(self, adapter, question_type):
        if question_type == "NUMERIC":
            val = adapter.get("correct_value")
            return val is not None
        if question_type == "BINARY":
            options = adapter.get("options") or []
            return any(o.get("is_correct") for o in options)
        return False

    def _upsert_question(self, adapter, text, text_hash, question_type):
        """Insert question; skip if the hash already exists. Returns the UUID or None."""
        self.cursor.execute(
            "SELECT id FROM questions WHERE text_hash = %s", (text_hash,)
        )
        row = self.cursor.fetchone()
        if row:
            return None  # duplicate — skip

        self.cursor.execute(
            """
            INSERT INTO questions
                (text, type, category, source_url, scraped_at, status,
                 correct_value, unit, tolerance_percent, text_hash)
            VALUES (%s, %s, %s, %s, %s, 'PENDING_REVIEW', %s, %s, %s, %s)
            RETURNING id
            """,
            (
                text,
                question_type,
                adapter.get("category"),
                adapter.get("source_url"),
                datetime.now(timezone.utc),
                adapter.get("correct_value"),
                adapter.get("unit"),
                adapter.get("tolerance_percent", 5),
                text_hash,
            ),
        )
        return self.cursor.fetchone()[0]

    def _upsert_options(self, question_id, options):
        for opt in options:
            self.cursor.execute(
                "INSERT INTO question_options (question_id, text, is_correct) VALUES (%s, %s, %s)",
                (question_id, opt["text"], opt.get("is_correct", False)),
            )

    def _resolve_spider_id(self, spider_name):
        try:
            self.cursor.execute(
                "SELECT id FROM spiders WHERE name = %s", (spider_name,)
            )
            row = self.cursor.fetchone()
            if row:
                self.cursor.execute(
                    "UPDATE spiders SET status = 'RUNNING', last_run_at = %s WHERE id = %s",
                    (datetime.now(timezone.utc), row[0]),
                )
                self.conn.commit()
                return row[0]
        except Exception:
            logger.warning("Could not resolve spider_id for '%s' — run will not be tracked", spider_name)
        return None

    def _start_run(self):
        self.cursor.execute(
            """
            INSERT INTO spider_runs (spider_id, started_at, questions_inserted, errors)
            VALUES (%s, %s, 0, 0)
            RETURNING id
            """,
            (self.spider_id, datetime.now(timezone.utc)),
        )
        self.run_id = self.cursor.fetchone()[0]
        self.conn.commit()

    def _finish_run(self):
        self.cursor.execute(
            """
            UPDATE spider_runs
            SET finished_at = %s, questions_inserted = %s, errors = %s
            WHERE id = %s
            """,
            (datetime.now(timezone.utc), self.questions_inserted, self.errors, self.run_id),
        )
        self.cursor.execute(
            "UPDATE spiders SET status = 'IDLE' WHERE id = %s",
            (self.spider_id,),
        )
