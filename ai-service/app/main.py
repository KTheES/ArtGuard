import asyncio
from contextlib import asynccontextmanager
import hmac
import logging
import threading
from fastapi import FastAPI, Header, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from .config import Settings, MODEL_ID, MODEL_REVISION, PREPROCESSING_VERSION
from .downloader import ImageDownloader
from .engine import DinoEngine
from .errors import ServiceError
from .images import decode_image
from .regions import REGION_SCHEME, crop_regions
from .hashes import PERCEPTUAL_HASH_VERSION, perceptual_hashes


class EmbeddingRequest(BaseModel):
    imageUrl: str = Field(min_length=1, max_length=4096)

    def __repr__(self):
        return "EmbeddingRequest(imageUrl=[REDACTED])"


class EmbeddingResponse(BaseModel):
    model: str = "dinov2"
    modelId: str = MODEL_ID
    version: str = MODEL_REVISION
    preprocessingVersion: str = PREPROCESSING_VERSION
    dimension: int = 768
    normalized: bool = True
    embedding: list[float] = Field(min_length=768, max_length=768)
    perceptualHashVersion: str = PERCEPTUAL_HASH_VERSION
    pHash: str = Field(pattern="^[a-f0-9]{16}$")
    dHash: str = Field(pattern="^[a-f0-9]{16}$")

class RegionEmbedding(BaseModel):
    key: str
    x: float = Field(ge=0, le=1)
    y: float = Field(ge=0, le=1)
    width: float = Field(gt=0, le=1)
    height: float = Field(gt=0, le=1)
    embedding: list[float] = Field(min_length=768, max_length=768)
    pHash: str = Field(pattern="^[a-f0-9]{16}$")
    dHash: str = Field(pattern="^[a-f0-9]{16}$")

class RegionEmbeddingResponse(EmbeddingResponse):
    regionScheme: str = REGION_SCHEME
    regions: list[RegionEmbedding] = Field(min_length=5, max_length=5)


class BodyLimitMiddleware:
    def __init__(self, app, limit=16384):
        self.app, self.limit = app, limit

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        body = bytearray()
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            body.extend(message.get("body", b""))
            if len(body) > self.limit:
                response = JSONResponse({"error": {"code": "REQUEST_TOO_LARGE", "message": "Request body exceeds 16 KiB."}}, status_code=413)
                return await response(scope, receive, send)
            if not message.get("more_body", False):
                break
        sent = False

        async def replay():
            nonlocal sent
            if not sent:
                sent = True
                return {"type": "http.request", "body": bytes(body), "more_body": False}
            return await receive()
        await self.app(scope, replay, send)


def create_app(settings=None, engine=None, downloader=None):
    settings = settings or Settings.from_env()
    engine = engine or DinoEngine(settings)
    downloader = downloader or ImageDownloader(settings)
    capacity = threading.BoundedSemaphore(1)

    @asynccontextmanager
    async def lifespan(app):
        try:
            await asyncio.to_thread(engine.load)
        except Exception:
            # Never log exception text: model/download URLs may contain credentials.
            logging.getLogger("artworkguard.ai").error("Embedding model unavailable; check local model cache and runtime.")
        yield

    app = FastAPI(title="ArtworkGuard AI", version="0.1.0", lifespan=lifespan)
    app.add_middleware(BodyLimitMiddleware)

    @app.exception_handler(ServiceError)
    async def service_error(request, exc):
        return JSONResponse({"error": {"code": exc.code, "message": exc.message}}, status_code=exc.status, headers={"Cache-Control": "no-store"})

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, exc):
        return JSONResponse({"error": {"code": "INVALID_REQUEST", "message": "A valid imageUrl is required."}}, status_code=422)

    @app.exception_handler(Exception)
    async def unexpected_error(request, exc):
        return JSONResponse({"error": {"code": "INTERNAL_ERROR", "message": "The request could not be processed."}}, status_code=500)

    @app.get("/health/live")
    def live():
        return {"status": "UP"}

    @app.get("/health/ready")
    def ready():
        if not engine.ready:
            raise ServiceError("MODEL_NOT_READY", "The embedding model is not ready.", 503)
        return {"status": "UP", "model": MODEL_ID, "version": MODEL_REVISION}

    @app.post("/v1/embeddings", response_model=EmbeddingResponse)
    def embeddings(request: EmbeddingRequest, response: Response, x_api_key: str | None = Header(default=None)):
        response.headers["Cache-Control"] = "no-store"
        if x_api_key is None or not hmac.compare_digest(x_api_key.encode(), settings.api_key.encode()):
            raise ServiceError("UNAUTHORIZED", "A valid service API key is required.", 401)
        if not engine.ready:
            raise ServiceError("MODEL_NOT_READY", "The embedding model is not ready.", 503)
        if not capacity.acquire(blocking=False):
            raise ServiceError("MODEL_BUSY", "The embedding service is busy. Retry later.", 429)
        try:
            data, content_type = downloader.fetch(request.imageUrl)
            with decode_image(data, content_type, settings.max_pixels) as image:
                result = engine.embed(image)
                phash, dhash = perceptual_hashes(image)
            return EmbeddingResponse(embedding=result, pHash=phash, dHash=dhash)
        finally:
            capacity.release()

    @app.post("/v1/region-embeddings", response_model=RegionEmbeddingResponse)
    def region_embeddings(request: EmbeddingRequest, response: Response, x_api_key: str | None = Header(default=None)):
        response.headers["Cache-Control"] = "no-store"
        if x_api_key is None or not hmac.compare_digest(x_api_key.encode(), settings.api_key.encode()):
            raise ServiceError("UNAUTHORIZED", "A valid service API key is required.", 401)
        if not engine.ready:
            raise ServiceError("MODEL_NOT_READY", "The embedding model is not ready.", 503)
        if not capacity.acquire(blocking=False):
            raise ServiceError("MODEL_BUSY", "The embedding service is busy. Retry later.", 429)
        try:
            data, content_type = downloader.fetch(request.imageUrl)
            with decode_image(data, content_type, settings.max_pixels) as image:
                crops = crop_regions(image)
                vectors = engine.embed_many([image] + [crop for _, crop in crops])
                hashes = [perceptual_hashes(image)] + [perceptual_hashes(crop) for _, crop in crops]
                regions = [RegionEmbedding(key=region.key, x=region.x, y=region.y, width=region.width,
                                           height=region.height, embedding=vector, pHash=hashes[index][0], dHash=hashes[index][1])
                           for index, ((region, _), vector) in enumerate(zip(crops, vectors[1:]), start=1)]
            return RegionEmbeddingResponse(embedding=vectors[0], pHash=hashes[0][0], dHash=hashes[0][1], regions=regions)
        finally:
            capacity.release()

    return app
