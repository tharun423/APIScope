import os
import httpx
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.prompts import PromptTemplate
from langchain_core.output_parsers import StrOutputParser
from pydantic import BaseModel
from typing import Generator

load_dotenv()

SPRING_API_URL = os.getenv("SPRING_API_URL", "http://localhost:8080")
ENDPOINTS_URL  = f"{SPRING_API_URL}/apiscope/api/endpoints"
CHROMA_DIR     = os.getenv("CHROMA_DIR", "./chroma_db")

llm = ChatGoogleGenerativeAI(
    temperature=0.1,
    google_api_key=os.getenv("GEMINI_API_KEY"),
    model=os.getenv("CHAT_MODEL", "gemini-1.5-flash"),
)

embeddings = GoogleGenerativeAIEmbeddings(
    google_api_key=os.getenv("GEMINI_API_KEY"),
    model=os.getenv("EMBED_MODEL", "models/embedding-001"),
)

vector_store = Chroma(
    collection_name="api_endpoints",
    embedding_function=embeddings,
    persist_directory=CHROMA_DIR,
)

RAG_PROMPT = PromptTemplate.from_template(
    "You are an API assistant for a Spring Boot application. "
    "Use ONLY the API endpoint context below to answer the question. "
    "If the context does not contain enough information, say so.\n\n"
    "Context:\n{context}\n\n"
    "Question: {question}\n\n"
    "Answer:"
)


def endpoint_to_text(ep: dict) -> str:
    lines = [
        f"{ep.get('httpMethod', 'GET')} {ep.get('path', '/')}",
        f"Controller: {ep.get('controllerName', '')}  Method: {ep.get('methodName', '')}",
        f"Description: {ep.get('description', '')}",
    ]
    if ep.get("pathParams"):
        lines.append(f"Path params: {', '.join(ep['pathParams'])}")
    if ep.get("requiredQueryParams"):
        lines.append(f"Required query params: {', '.join(ep['requiredQueryParams'])}")
    if ep.get("optionalQueryParams"):
        lines.append(f"Optional query params: {', '.join(ep['optionalQueryParams'])}")
    if ep.get("requestBodyType"):
        lines.append(f"Request body: {ep['requestBodyType']}")
    if ep.get("responseType"):
        lines.append(f"Response type: {ep['responseType']}")
    return "\n".join(lines)


def ingest_endpoints(endpoints: list[dict]) -> int:
    # Upsert — delete existing docs then re-add so re-embed is always fresh
    existing = vector_store.get()
    if existing and existing.get("ids"):
        vector_store.delete(ids=existing["ids"])

    docs = [
        Document(
            page_content=endpoint_to_text(ep),
            metadata={
                "path": ep.get("path", ""),
                "httpMethod": ep.get("httpMethod", ""),
                "method": ep.get("methodName", ""),
            },
            id=f"{ep.get('httpMethod', '')}:{ep.get('path', '')}",
        )
        for ep in endpoints
    ]
    vector_store.add_documents(docs)
    return len(docs)


def build_rag_response(question: str) -> str:
    docs    = vector_store.as_retriever(search_kwargs={"k": 5}).invoke(question)
    context = "\n\n---\n\n".join(d.page_content for d in docs)
    return (RAG_PROMPT | llm | StrOutputParser()).invoke({"context": context, "question": question})


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        resp = httpx.get(ENDPOINTS_URL, timeout=10)
        resp.raise_for_status()
        count = ingest_endpoints(resp.json())
        print(f"[APIScope] Embedded {count} endpoints into ChromaDB.")
    except Exception as e:
        print(f"[APIScope] Warning: could not auto-embed endpoints on startup: {e}")
    yield


app = FastAPI(title="APIScope LLM Service", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


class ChatRequest(BaseModel):
    question: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/embed")
def embed():
    """Re-fetch endpoints from Spring and re-embed into ChromaDB."""
    try:
        resp = httpx.get(ENDPOINTS_URL, timeout=10)
        resp.raise_for_status()
        return {"embedded": ingest_endpoints(resp.json())}
    except Exception as e:
        return {"error": str(e)}


@app.post("/ingest")
def ingest(endpoints: list[dict]):
    """Accept endpoints pushed directly from any Spring Boot app."""
    try:
        return {"embedded": ingest_endpoints(endpoints)}
    except Exception as e:
        return {"error": str(e)}


@app.post("/chat")
def chat(req: ChatRequest):
    return {"answer": build_rag_response(req.question)}


@app.post("/chat/stream")
def chat_stream(req: ChatRequest):
    docs    = vector_store.as_retriever(search_kwargs={"k": 5}).invoke(req.question)
    context = "\n\n---\n\n".join(d.page_content for d in docs)

    def generate() -> Generator[str, None, None]:
        for token in (RAG_PROMPT | llm | StrOutputParser()).stream({"context": context, "question": req.question}):
            if token:
                yield f"event: token\ndata: {token}\n\n"
        yield "event: done\ndata: [DONE]\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")
