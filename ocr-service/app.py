"""Local OCR sidecar for receipts and purchase statements.

POST /ocr  body = raw image (PNG/JPEG/WEBP) or PDF bytes, Content-Type set accordingly.
Response:  {"engine": "paddleocr", "pages": n, "lines": [{"page": 0, "text": "...", "confidence": 0.97,
           "box": [[x, y], [x, y], [x, y], [x, y]]}]}
GET /health -> {"status": "UP"}

Runs fully offline: models are downloaded at image build time. Nothing is written to disk except a
temporary file for PDF rendering, and request bodies are never logged.
"""

import gc
import json
import logging
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import cv2
import fitz  # PyMuPDF
import numpy as np
from paddle import inference as _paddle_inference
from paddleocr import PaddleOCR

MAX_BYTES = int(os.environ.get("OCR_MAX_BYTES", str(15 * 1024 * 1024)))
MAX_PDF_PAGES = int(os.environ.get("OCR_MAX_PDF_PAGES", "5"))
LANG = os.environ.get("OCR_LANG", "korean")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s", force=True)
log = logging.getLogger("ocr")
logging.getLogger("ppocr").setLevel(logging.WARNING)

_model_dirs = {key: os.environ[env] for key, env in (
    ("det_model_dir", "OCR_DET_MODEL_DIR"),
    ("rec_model_dir", "OCR_REC_MODEL_DIR"),
    ("cls_model_dir", "OCR_CLS_MODEL_DIR"),
) if os.environ.get(env)}


# Receipts and statements are photographed upright, so the angle classifier is off unless OCR_USE_ANGLE_CLS=true.
USE_CLS = os.environ.get("OCR_USE_ANGLE_CLS", "false").lower() == "true"
ENABLE_MKLDNN = os.environ.get("OCR_ENABLE_MKLDNN", "false").lower() == "true"

# Paddle inference turns oneDNN on by default for x86 CPUs and PaddleOCR only ever enables it, so
# enable_mkldnn=False alone leaves it on. oneDNN then fails with "could not execute a primitive" once the input
# size changes between requests.
_create_predictor = _paddle_inference.create_predictor


def _create_predictor_without_mkldnn(config):
    if not ENABLE_MKLDNN:
        config.disable_mkldnn()
    return _create_predictor(config)


_paddle_inference.create_predictor = _create_predictor_without_mkldnn


def _new_engine():
    return PaddleOCR(use_angle_cls=USE_CLS, lang=LANG, show_log=False, enable_mkldnn=ENABLE_MKLDNN,
                     cpu_threads=int(os.environ.get("OCR_CPU_THREADS", "4")),
                     # Without oneDNN a batch of 6 text crops peaks around 2.6GB; one crop at a time stays near 1GB.
                     rec_batch_num=int(os.environ.get("OCR_REC_BATCH_NUM", "1")),
                     det_limit_side_len=int(os.environ.get("OCR_DET_LIMIT_SIDE_LEN", "960")), **_model_dirs)


_engine = _new_engine()
log.setLevel(logging.INFO)
_lock = threading.Lock()


def _images_from_body(body: bytes, content_type: str):
    if content_type.startswith("application/pdf") or body[:4] == b"%PDF":
        doc = fitz.open(stream=body, filetype="pdf")
        try:
            for index, page in enumerate(doc):
                if index >= MAX_PDF_PAGES:
                    break
                pix = page.get_pixmap(dpi=200)
                img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
                if pix.n == 4:
                    img = cv2.cvtColor(img, cv2.COLOR_RGBA2BGR)
                else:
                    img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
                yield img
        finally:
            doc.close()
        return
    img = cv2.imdecode(np.frombuffer(body, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("unsupported image")
    yield img


def run_ocr(body: bytes, content_type: str):
    global _engine
    lines = []
    pages = 0
    for page_index, img in enumerate(_images_from_body(body, content_type)):
        pages += 1
        with _lock:
            try:
                result = _engine.ocr(img, cls=USE_CLS)
            except RuntimeError:
                # A predictor left in a broken state is recovered by a fresh one; the old one is released first
                # so two engines never sit in memory together.
                log.warning("ocr runtime error, rebuilding engine and retrying once")
                _engine = None
                gc.collect()
                _engine = _new_engine()
                result = _engine.ocr(img, cls=USE_CLS)
        for block in result or []:
            for item in block or []:
                box, (text, confidence) = item[0], item[1]
                lines.append({
                    "page": page_index,
                    "text": text,
                    "confidence": round(float(confidence), 4),
                    "box": [[round(float(x), 1), round(float(y), 1)] for x, y in box],
                })
    return {"engine": "paddleocr", "pages": pages, "lines": lines}


class Handler(BaseHTTPRequestHandler):
    server_version = "shop-ocr/1.0"

    def _send(self, status: int, payload: dict):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"status": "UP", "engine": "paddleocr", "lang": LANG})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/ocr":
            self._send(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BYTES:
            self._send(413, {"error": "empty or too large"})
            return
        body = self.rfile.read(length)
        try:
            result = run_ocr(body, self.headers.get("Content-Type") or "")
            log.info("ocr ok bytes=%d pages=%d lines=%d", length, result["pages"], len(result["lines"]))
            self._send(200, result)
        except ValueError:
            self._send(415, {"error": "unsupported file"})
        except Exception:  # noqa: BLE001 - never leak internals to the caller
            log.exception("ocr failed")
            self._send(500, {"error": "ocr failed"})

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    port = int(os.environ.get("OCR_PORT", "8866"))
    log.info("local OCR listening on %d (lang=%s)", port, LANG)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
