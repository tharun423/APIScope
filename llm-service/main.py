"""
APIScope LLM Service
Handles embeddings, vector search, and Ollama chat.

Start: uvicorn main:app --port 8000

Environment variables (all optional):
  CHAT_MODEL   = llama3.2
  EMBED_MODEL  = nomic-embed-text
  VECTOR_STORE = vector_store.json
  TOP_K        = 5
  TEMPERATURE  = 0.1
"""
import json
import os
from pathlib import Path
from typing import Generator

import ollama
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

# ── Config ────────────────────────────────────────────────────────────────────
CHAT_MODEL   = os.getenv("CHAT_MODEL",   "llama3.2")
EMBED_MODEL  = os.getenv("EMBED_MODEL",  "nomic-embed-text")
VECTOR_STORE = os.getenv("VECTOR_STORE", "vector_store.json")
TOP_K        = int(os.getenv("TOP_K",    "5"))
TEMPERATURE  = float(os.getenv("TEMPERATURE", "0.1"))

SYSTEM_PROMPT = """\
You are an expert API assistant embedded inside developer documentation.
Answer ONLY using the API context provided. Do not invent endpoints.
Generate concise Java or React snippets using exact paths and field names.
If the answer is not in the context say: "I could not find a relevant endpoint for that."

API Context:
---
{context}
---"""

# ── In-memory vector store ────────────────────────────────────────────────────
# Each entry: { "text": str, "embedding": list[float] }
store: list[dict] = []


def load_store():
    global store
    path = Path(VECTOR_STORE)
    if path.exists():
        store = json.loads(path.read_text())


def save_store():
    Path(VECTOR_STORE).write_text(json.dumps(store))


def cosine_similarity(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = sum(x * x for x in a) ** 0.5
    norm_b = sum(x * x for x in b) ** 0.5
    return dot / (norm_a * norm_b) if norm_a and norm_b else 0.0


def search(query: str, top_k: int) -> list[str]:
    if not store:
        return []
    query_vec = ollama.embeddings(model=EMBED_MODEL, prompt=query)["embedding"]
    ranked = sorted(store, key=lambda d: cosine_similarity(query_vec, d["embedding"]), reverse=True)
    return [d["text"] for d in ranked[:top_k]]


def endpoint_to_text(ep: dict) -> str:
    return (
        f"Endpoint: [{ep.get('httpMethod', 'GET')}] {ep.get('path', '')}\n"
        f"Controller: {ep.get('controllerName', '')}\n"
        f"Method: {ep.get('methodName', '')}\n"
        f"Path Params: {', '.join(ep.get('pathParams') or []) or 'none'}\n"
        f"Required Params: {', '.join(ep.get('requiredQueryParams') or []) or 'none'}\n"
        f"Optional Params: {', '.join(ep.get('optionalQueryParams') or []) or 'none'}\n"
        f"Request Body: {ep.get('requestBodyType') or 'none'}\n"
        f"Response Type: {ep.get('responseType') or 'void'}\n"
        f"Summary: {ep.get('description', '')}"
    )


# ── Schemas ───────────────────────────────────────────────────────────────────
class IngestRequest(BaseModel):
    endpoints: list[dict]


class ChatRequest(BaseModel):
    question: str
    top_k: int = TOP_K


# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(title="APIScope LLM Service")


@app.on_event("startup")
def startup():
    load_store()


@app.get("/health")
def health():
    return {"status": "ok", "docs_indexed": len(store)}


@app.post("/ingest")
def ingest(req: IngestRequest):
    global store
    store = []
    for ep in req.endpoints:
        text = ep.get("llmText") or endpoint_to_text(ep)
        embedding = ollama.embeddings(model=EMBED_MODEL, prompt=text)["embedding"]
        store.append({"text": text, "embedding": embedding})
    save_store()
    return {"ingested": len(store)}


@app.post("/chat")
def chat(req: ChatRequest):
    context = "\n---\n".join(search(req.question, req.top_k)) or "No context available."
    prompt = SYSTEM_PROMPT.format(context=context)
    response = ollama.chat(
        model=CHAT_MODEL,
        messages=[
            {"role": "system", "content": prompt},
            {"role": "user",   "content": req.question},
        ],
        options={"temperature": TEMPERATURE},
    )
    return {"answer": response["message"]["content"]}


@app.post("/chat/stream")
def chat_stream(req: ChatRequest):
    context = "\n---\n".join(search(req.question, req.top_k)) or "No context available."
    prompt = SYSTEM_PROMPT.format(context=context)

    def token_generator() -> Generator[str, None, None]:
        for chunk in ollama.chat(
            model=CHAT_MODEL,
            messages=[
                {"role": "system", "content": prompt},
                {"role": "user",   "content": req.question},
            ],
            options={"temperature": TEMPERATURE},
            stream=True,
        ):
            token = chunk["message"]["content"]
            if token:
                yield f"data: {token}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(token_generator(), media_type="text/event-stream")
