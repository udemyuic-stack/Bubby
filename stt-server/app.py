import hmac
import os
import threading

from fastapi import FastAPI, HTTPException, Request
from faster_whisper import WhisperModel

MODEL_DIR = os.environ.get("MODEL_DIR", "/home/app/model")
TOKEN = os.environ.get("STT_TOKEN", "")
MAX_BYTES = 2 * 1024 * 1024
LANGUAGE = "uz"
BEAM_SIZE = 1
THREADS = int(os.environ.get("STT_THREADS", str(min(os.cpu_count() or 2, 16))))

app = FastAPI()
model = WhisperModel(MODEL_DIR, device="cpu", compute_type="int8", cpu_threads=THREADS)
busy = threading.Semaphore(1)


def warm_up():
    import numpy as np

    list(model.transcribe(np.zeros(16000, dtype=np.float32), language=LANGUAGE, beam_size=1)[0])


warm_up()


def authorized(header: str) -> bool:
    expected = f"Bearer {TOKEN}"
    return bool(TOKEN) and hmac.compare_digest(header.encode(), expected.encode())


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/transcribe")
async def transcribe(request: Request):
    if not authorized(request.headers.get("authorization", "")):
        raise HTTPException(status_code=401, detail="unauthorized")
    declared = request.headers.get("content-length")
    if declared and declared.isdigit() and int(declared) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="audio too large")
    body = await request.body()
    if not body:
        raise HTTPException(status_code=400, detail="empty audio")
    if len(body) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="audio too large")
    if not busy.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="busy")
    try:
        return {"text": run(body)}
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=422, detail="cannot decode audio")
    finally:
        busy.release()


def run(body: bytes) -> str:
    import io

    segments, _ = model.transcribe(
        io.BytesIO(body), language=LANGUAGE, beam_size=BEAM_SIZE, vad_filter=True, condition_on_previous_text=False
    )
    return " ".join(s.text.strip() for s in segments).strip()
