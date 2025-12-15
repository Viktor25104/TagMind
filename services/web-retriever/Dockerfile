FROM python:3.12-slim
WORKDIR /app
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*
COPY services/web-retriever/stub/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY services/web-retriever/stub/main.py .
EXPOSE 8083
ENTRYPOINT ["uvicorn","main:app","--host","0.0.0.0","--port","8083"]
