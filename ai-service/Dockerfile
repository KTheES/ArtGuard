FROM python:3.12-slim
WORKDIR /service
COPY requirements-cpu.txt requirements.txt ./
RUN pip install --no-cache-dir -r requirements-cpu.txt && pip install --no-cache-dir -r requirements.txt
COPY app ./app
RUN useradd --create-home --uid 10001 artworkguard && mkdir /models && chown artworkguard /models
USER artworkguard
ENV AI_MODEL_CACHE=/models
EXPOSE 8001
CMD ["python","-m","uvicorn","app.main:create_app","--factory","--host","0.0.0.0","--port","8001","--workers","1","--no-access-log"]
