from io import BytesIO
from concurrent.futures import ThreadPoolExecutor
import threading
from fastapi.testclient import TestClient
from PIL import Image
import pytest
from app.config import Settings
from app.main import create_app
from app.errors import ServiceError

KEY = "test-only-api-key-0123456789abcdef"


class Engine:
    ready = False
    def load(self):
        self.ready = True
    def embed(self, image):
        return [1.0] + [0.0] * 767
    def embed_many(self, images):
        return [self.embed(image) for image in images]


class Downloader:
    def fetch(self, url):
        stream = BytesIO()
        Image.new("RGB", (32, 32), "red").save(stream, format="PNG")
        return stream.getvalue(), "image/png"


def client(engine=None, downloader=None):
    return TestClient(create_app(Settings(api_key=KEY), engine or Engine(), downloader or Downloader()))


def test_valid_image_returns_versioned_embedding():
    with client() as api:
        response = api.post("/v1/embeddings", json={"imageUrl": "https://storage.test/image"}, headers={"X-API-Key": KEY})
        assert response.status_code == 200
        data = response.json()
        assert data["dimension"] == 768 and len(data["embedding"]) == 768
        assert data["normalized"] and len(data["version"]) == 40
        assert len(data["pHash"]) == 16 and len(data["dHash"]) == 16
        assert api.get("/health/ready").status_code == 200

def test_region_embeddings_include_full_image_and_bounded_regions():
    with client() as api:
        response = api.post("/v1/region-embeddings", json={"imageUrl": "https://storage.test/image"}, headers={"X-API-Key": KEY})
        assert response.status_code == 200
        data = response.json()
        assert data["regionScheme"] == "fixed-overlap-5-v1"
        assert len(data["embedding"]) == 768 and len(data["regions"]) == 5
        assert data["perceptualHashVersion"] == "phash32-dhash9-luma-v1"
        assert all(len(item["pHash"]) == 16 and len(item["dHash"]) == 16 for item in data["regions"])
        assert {item["key"] for item in data["regions"]} == {"CENTER", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"}
        assert all(item["x"] + item["width"] <= 1 and item["y"] + item["height"] <= 1 for item in data["regions"])


@pytest.mark.parametrize("key", [None, "wrong"])
def test_authentication_required(key):
    with client() as api:
        headers = {} if key is None else {"X-API-Key": key}
        assert api.post("/v1/embeddings", json={"imageUrl": "https://storage.test/image"}, headers=headers).status_code == 401


def test_validation_errors_do_not_echo_signed_url():
    secret_url = "https://storage.test/image?secret=signed-token"
    with client() as api:
        response = api.post("/v1/embeddings", json={"imageUrl": {"secret": secret_url}}, headers={"X-API-Key": KEY})
        assert response.status_code == 422
        assert "signed-token" not in response.text


def test_large_request_is_bounded():
    with client() as api:
        response = api.post("/v1/embeddings", content=b"x" * 17000, headers={"Content-Type": "application/json", "X-API-Key": KEY})
        assert response.status_code == 413


def test_unready_model_never_returns_fake_embedding():
    class Broken(Engine):
        def load(self):
            raise RuntimeError("sensitive-model-path")
    with client(Broken()) as api:
        assert api.get("/health/live").status_code == 200
        assert api.get("/health/ready").status_code == 503
        response = api.post("/v1/embeddings", json={"imageUrl": "https://storage.test/image"}, headers={"X-API-Key": KEY})
        assert response.status_code == 503 and "embedding" not in response.json()


def test_download_errors_are_sanitized():
    class Broken(Downloader):
        def fetch(self, url):
            raise ServiceError("IMAGE_ORIGIN_DENIED", "Image origin denied.", 403)
    with client(downloader=Broken()) as api:
        assert api.post("/v1/embeddings", json={"imageUrl": "https://wrong.test"}, headers={"X-API-Key": KEY}).status_code == 403


def test_concurrent_inference_is_rejected_without_queueing():
    entered, release = threading.Event(), threading.Event()
    class Slow(Engine):
        def embed(self, image):
            entered.set()
            assert release.wait(10)
            return super().embed(image)
    with client(Slow()) as api, ThreadPoolExecutor(max_workers=1) as executor:
        def request():
            return api.post("/v1/embeddings", json={"imageUrl": "https://storage.test/image"}, headers={"X-API-Key": KEY})
        pending = executor.submit(request)
        assert entered.wait(5)
        try:
            assert request().status_code == 429
        finally:
            release.set()
        assert pending.result(timeout=10).status_code == 200
