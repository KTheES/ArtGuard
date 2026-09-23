"""Opt-in real CPU model test for the three bundled product fixtures."""
import os
import threading
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import numpy as np
import pytest
from fastapi.testclient import TestClient
from app.config import Settings
from app.engine import DinoEngine
from app.main import create_app

pytestmark = pytest.mark.model

def test_mock_product_images_through_real_ai_api():
    fixtures = Path(__file__).resolve().parents[2] / "backend/src/main/resources/mock-marketplace/images"
    payloads = {"/" + path.name: path.read_bytes() for path in fixtures.glob("*.png")}
    assert len(payloads) == 3

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            data = payloads.get(self.path)
            if data is None:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    origin = f"http://127.0.0.1:{server.server_port}"
    settings = Settings(api_key="product-fixture-test-0123456789abcdef",
                        cache_dir=os.getenv("AI_MODEL_CACHE", ".model-cache"),
                        allowed_origins=(origin,), allow_private_images=True)
    try:
        engine = DinoEngine(settings)
        engine.load()
        with TestClient(create_app(settings, engine)) as api:
            for path in sorted(payloads):
                response = api.post("/v1/embeddings", json={"imageUrl": origin + path},
                                    headers={"X-API-Key": settings.api_key})
                assert response.status_code == 200, response.text
                result = response.json()
                vector = np.asarray(result["embedding"])
                assert result["dimension"] == 768 and result["normalized"]
                assert vector.shape == (768,) and np.isfinite(vector).all()
                assert abs(np.linalg.norm(vector) - 1) < 1e-5
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)
