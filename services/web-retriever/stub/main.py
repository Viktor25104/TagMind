from fastapi import FastAPI

app = FastAPI()


@app.get("/healthz")
def healthz():
    return "ok"


@app.get("/")
def root():
    return "web-retriever stub"
