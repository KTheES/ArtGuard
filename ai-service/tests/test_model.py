import os
from io import BytesIO
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
import numpy as np
from PIL import Image, ImageDraw
import pytest
from fastapi.testclient import TestClient
from app.config import Settings
from app.engine import DinoEngine
from app.main import create_app

pytestmark = pytest.mark.model


def test_real_model_and_local_http_api():
    stream = BytesIO()
    image = Image.new("RGB", (320, 240), "white")
    draw = ImageDraw.Draw(image)
    draw.rectangle((40, 40, 160, 180), fill="navy")
    draw.ellipse((160, 50, 290, 190), fill="orange")
    image.save(stream, format="PNG")
    payload = stream.getvalue()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        def log_message(self, format, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    origin = f"http://127.0.0.1:{server.server_port}"
    settings = Settings(api_key="real-model-test-key-0123456789abcdef",
                        cache_dir=os.getenv("AI_MODEL_CACHE", ".model-cache"),
                        allowed_origins=(origin,), allow_private_images=True)
    engine = DinoEngine(settings)
    try:
        engine.load()
        first = np.array(engine.embed(image))
        second = np.array(engine.embed(image))
        assert first.shape == (768,) and np.isfinite(first).all()
        assert abs(np.linalg.norm(first) - 1.0) < 1e-5
        np.testing.assert_allclose(first, second, atol=1e-6)
        with TestClient(create_app(settings, engine)) as api:
            assert api.get("/health/ready").status_code == 200
            response = api.post("/v1/embeddings", json={"imageUrl": origin + "/artwork.png"}, headers={"X-API-Key": settings.api_key})
            assert response.status_code == 200, response.text
            np.testing.assert_allclose(response.json()["embedding"], first, atol=1e-6)
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)
