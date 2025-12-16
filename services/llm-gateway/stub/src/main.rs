use axum::{routing::get, Router};
use std::net::SocketAddr;

async fn healthz() -> &'static str {
    "ok"
}
async fn root() -> &'static str {
    "llm-gateway stub"
}

#[tokio::main]
async fn main() {
    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/", get(root));

    let addr = SocketAddr::from(([0, 0, 0, 0], 8084));
    println!("llm-gateway listening on {}", addr);
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
