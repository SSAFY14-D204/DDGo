from __future__ import annotations

import gzip
import logging
from collections.abc import Iterable

from fastapi import status
from fastapi.responses import JSONResponse
from starlette.datastructures import Headers, MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

logger = logging.getLogger(__name__)


class GzipRequestMiddleware:
    """Decompress JSON request bodies sent with `Content-Encoding: gzip`."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        headers = Headers(scope=scope)
        encodings = _parse_content_encodings(headers.get("content-encoding"))
        if "gzip" not in encodings:
            await self.app(scope, receive, send)
            return

        unsupported_encodings = [encoding for encoding in encodings if encoding != "gzip"]
        if unsupported_encodings:
            await JSONResponse(
                status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
                content={"detail": "Unsupported request content encoding."},
            )(scope, receive, send)
            return

        try:
            compressed_body = await _read_request_body(receive)
            decompressed_body = gzip.decompress(compressed_body)
        except (OSError, EOFError) as exc:
            logger.warning("Rejected invalid gzip request: path=%s detail=%s", scope.get("path"), exc)
            await JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={"detail": "Invalid gzip request body."},
            )(scope, receive, send)
            return

        new_scope = dict(scope)
        new_scope["headers"] = _rewrite_headers(
            raw_headers=scope.get("headers", ()),
            body_length=len(decompressed_body),
        )
        await self.app(new_scope, _build_receive(decompressed_body), send)


async def _read_request_body(receive: Receive) -> bytes:
    chunks: list[bytes] = []
    more_body = True
    while more_body:
        message = await receive()
        if message["type"] != "http.request":
            continue
        chunks.append(message.get("body", b""))
        more_body = bool(message.get("more_body", False))
    return b"".join(chunks)


def _build_receive(body: bytes) -> Receive:
    consumed = False

    async def receive() -> Message:
        nonlocal consumed
        if consumed:
            return {
                "type": "http.request",
                "body": b"",
                "more_body": False,
            }

        consumed = True
        return {
            "type": "http.request",
            "body": body,
            "more_body": False,
        }

    return receive


def _parse_content_encodings(value: str | None) -> list[str]:
    if not value:
        return []
    return [token.strip().lower() for token in value.split(",") if token.strip()]


def _rewrite_headers(raw_headers: Iterable[tuple[bytes, bytes]], body_length: int) -> list[tuple[bytes, bytes]]:
    scope = {"headers": list(raw_headers)}
    headers = MutableHeaders(scope=scope)
    if "content-encoding" in headers:
        del headers["content-encoding"]
    headers["content-length"] = str(body_length)
    return list(scope["headers"])
