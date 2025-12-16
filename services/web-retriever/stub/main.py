from __future__ import annotations

import os
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Optional

from fastapi import FastAPI, Header, Response
from pydantic import BaseModel, Field

app = FastAPI(title="TagMind Web Retriever (stub)", version="0.1.0")


def _new_request_id() -> str:
    return f"req_{uuid.uuid4().hex[:24]}"


def _get_or_create_request_id(x_request_id: Optional[str]) -> str:
    if x_request_id:
        s = x_request_id.strip()
        if 8 <= len(s) <= 128:
            return s
    return _new_request_id()


class ErrorResponse(BaseModel):
    requestId: str
    code: str
    message: str


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=512)
    recencyDays: int = Field(default=30, ge=0, le=3650)
    maxResults: int = Field(default=5, ge=1, le=10)
    lang: Optional[str] = Field(default="en")
    safe: bool = Field(default=True)
    allowNoContext: bool = Field(default=True)


class SearchResult(BaseModel):
    title: str
    snippet: str
    url: str
    source: Optional[str] = None
    publishedAt: Optional[str] = None  # ISO date-time


class SearchResponse(BaseModel):
    requestId: str
    results: list[SearchResult]


@app.get("/healthz")
def healthz(
    response: Response,
    x_request_id: Optional[str] = Header(default=None, alias="X-Request-Id"),
) -> str:
    req_id = _get_or_create_request_id(x_request_id)
    response.headers["X-Request-Id"] = req_id
    return "ok"


@app.post(
    "/v1/search",
    response_model=SearchResponse,
    responses={
        400: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
)
def search(
    body: SearchRequest,
    response: Response,
    x_request_id: Optional[str] = Header(default=None, alias="X-Request-Id"),
) -> Any:
    req_id = _get_or_create_request_id(x_request_id)
    response.headers["X-Request-Id"] = req_id

    q = body.query.strip()
    if not q:
        return ErrorResponse(
            requestId=req_id, code="BAD_REQUEST", message="query is required"
        )

    # Phase 4 stub: deterministic fake results (no Google CSE yet)
    now = datetime.now(timezone.utc).isoformat()
    base_url = os.environ.get("RETRIEVER_STUB_BASE_URL", "https://example.com")

    # A tiny delay to emulate network
    time.sleep(0.05)

    results: list[SearchResult] = [
        SearchResult(
            title=f"Stub result #{i+1} for: {q}",
            snippet="stub: this is a fake search snippet; real Google CSE will be added later",
            url=f"{base_url}/search?q={uuid.uuid4().hex}",
            source="stub",
            publishedAt=now,
        )
        for i in range(min(body.maxResults, 5))
    ]

    return SearchResponse(requestId=req_id, results=results)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8083)
