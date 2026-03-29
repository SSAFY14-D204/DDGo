from __future__ import annotations

import gzip
import unittest

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.gzip_request_middleware import GzipRequestMiddleware


def create_app() -> FastAPI:
    app = FastAPI()
    app.add_middleware(GzipRequestMiddleware)

    @app.post("/echo")
    async def echo(payload: dict[str, object]) -> dict[str, object]:
        return payload

    return app


class GzipRequestMiddlewareTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(create_app())

    def test_plain_json_request_is_untouched(self) -> None:
        response = self.client.post("/echo", json={"mode": "plain", "frame_count": 3})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"mode": "plain", "frame_count": 3})

    def test_non_gzip_content_encoding_is_passed_through(self) -> None:
        response = self.client.post(
            "/echo",
            content=b'{"mode":"identity","frame_count":5}',
            headers={
                "Content-Encoding": "identity",
                "Content-Type": "application/json",
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"mode": "identity", "frame_count": 5})

    def test_gzip_json_request_is_decompressed(self) -> None:
        payload = b'{"mode":"gzip","frame_count":9}'
        response = self.client.post(
            "/echo",
            content=gzip.compress(payload),
            headers={
                "Content-Encoding": "gzip",
                "Content-Type": "application/json",
            },
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"mode": "gzip", "frame_count": 9})

    def test_invalid_gzip_request_returns_400(self) -> None:
        response = self.client.post(
            "/echo",
            content=b"not-a-valid-gzip-stream",
            headers={
                "Content-Encoding": "gzip",
                "Content-Type": "application/json",
            },
        )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json(), {"detail": "Invalid gzip request body."})


if __name__ == "__main__":
    unittest.main()
