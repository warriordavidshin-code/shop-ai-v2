FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    OCR_PORT=8866 \
    OCR_LANG=korean

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 libglib2.0-0 libgl1 curl \
    && rm -rf /var/lib/apt/lists/*

ARG PADDLE_VERSION=3.0.0
ARG PADDLEOCR_VERSION=2.10.0
RUN pip install "numpy<2" "paddlepaddle==${PADDLE_VERSION}" "paddleocr==${PADDLEOCR_VERSION}" "opencv-python-headless<4.11" "PyMuPDF==1.24.10" \
    && pip install --force-reinstall --no-deps "pyclipper==1.3.0.post6" \
    && python -c "import pyclipper, cv2, fitz, paddle"

# Models are fetched with curl/tar: extracting them from Python after paddle is loaded segfaults in zlib.
ENV OCR_DET_MODEL_DIR=/opt/ocr-models/det \
    OCR_REC_MODEL_DIR=/opt/ocr-models/rec \
    OCR_CLS_MODEL_DIR=/opt/ocr-models/cls
RUN set -eux; \
    fetch() { mkdir -p "$1"; curl -fsSL "$2" | tar -x -C "$1" --strip-components=1; test -f "$1/inference.pdmodel"; }; \
    fetch "$OCR_DET_MODEL_DIR" https://paddleocr.bj.bcebos.com/PP-OCRv3/multilingual/Multilingual_PP-OCRv3_det_infer.tar; \
    fetch "$OCR_REC_MODEL_DIR" https://paddleocr.bj.bcebos.com/PP-OCRv4/multilingual/korean_PP-OCRv4_rec_infer.tar; \
    fetch "$OCR_CLS_MODEL_DIR" https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar

RUN useradd --create-home --uid 10001 ocr
USER ocr
WORKDIR /home/ocr/app

COPY --chown=ocr:ocr app.py .
RUN python -c "import app" 

EXPOSE 8866
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8866/health || exit 1

CMD ["python", "app.py"]
