use axum::{
    extract::{Json, State},
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
    routing::{get, post},
    Router,
};
use std::{net::SocketAddr, sync::Arc};
use tokio::signal;

#[derive(Clone)]
struct AppState {}

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct Citation {
    url: String,
    title: Option<String>,
    snippet: String,
}

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct CompleteRequest {
    prompt: String,
    locale: Option<String>,
    model: Option<String>,
    temperature: Option<f64>,
    max_tokens: Option<u32>,
    citations: Option<Vec<Citation>>,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct CompleteResponse {
    request_id: String,
    text: String,
    usage: serde_json::Value,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct ErrorResponse {
    request_id: String,
    code: String,
    message: String,
}

fn new_request_id() -> String {
    let bytes: [u8; 12] = rand::random();
    format!("req_{}", hex::encode(bytes))
}

fn get_or_create_request_id(headers: &HeaderMap) -> String {
    if let Some(v) = headers.get("x-request-id") {
        if let Ok(s) = v.to_str() {
            let s = s.trim();
            if (8..=128).contains(&s.len()) {
                return s.to_string();
            }
        }
    }
    new_request_id()
}

async fn healthz(headers: HeaderMap) -> impl IntoResponse {
    let request_id = get_or_create_request_id(&headers);
    let mut resp_headers = HeaderMap::new();
    resp_headers.insert("x-request-id", request_id.parse().unwrap());
    (resp_headers, "ok\n")
}

async fn complete(
    State(_state): State<Arc<AppState>>,
    headers: HeaderMap,
    Json(body): Json<CompleteRequest>,
) -> impl IntoResponse {
    let request_id = get_or_create_request_id(&headers);

    if body.prompt.trim().is_empty() {
        let err = ErrorResponse {
            request_id: request_id.clone(),
            code: "BAD_REQUEST".to_string(),
            message: "prompt is required".to_string(),
        };
        let mut resp_headers = HeaderMap::new();
        resp_headers.insert("x-request-id", request_id.parse().unwrap());
        return (StatusCode::BAD_REQUEST, resp_headers, Json(err)).into_response();
    }

    // Phase 4 stub: no Gemini call yet.
    let locale = body.locale.unwrap_or_else(|| "ru-RU".to_string());
    let model = body.model.unwrap_or_else(|| "stub".to_string());
    let temperature = body.temperature.unwrap_or(0.7);
    let max_tokens = body.max_tokens.unwrap_or(1024);

    // Use citations fields so clippy doesn't fail under -D warnings.
    let (citations_count, citations_preview) = match body.citations.as_ref() {
        None => (0usize, Vec::<String>::new()),
        Some(cits) => {
            let preview = cits
                .iter()
                .take(3)
                .map(|c| {
                    let t = c.title.clone().unwrap_or_else(|| "untitled".to_string());
                    let snip = if c.snippet.len() > 80 {
                        format!("{}…", &c.snippet[..80])
                    } else {
                        c.snippet.clone()
                    };
                    format!("{} | {} | {}", t, c.url, snip)
                })
                .collect::<Vec<_>>();
            (cits.len(), preview)
        }
    };

    let text = format!(
        "stub: completion generated (model={}, locale={}, temperature={}, maxTokens={}, citations={}, preview={:?})",
        model, locale, temperature, max_tokens, citations_count, citations_preview
    );

    let resp = CompleteResponse {
        request_id: request_id.clone(),
        text,
        usage: serde_json::json!({
            "model": model,
            "locale": locale,
            "temperature": temperature,
            "maxTokens": max_tokens,
            "citations": citations_count,
            "stub": true
        }),
    };

    let mut resp_headers = HeaderMap::new();
    resp_headers.insert("x-request-id", request_id.parse().unwrap());
    (StatusCode::OK, resp_headers, Json(resp)).into_response()
}

#[tokio::main]
async fn main() {
    let state = Arc::new(AppState {});

    let app = Router::new()
        .route("/healthz", get(healthz))
        .route("/v1/complete", post(complete))
        .with_state(state);

    let addr: SocketAddr = "0.0.0.0:8084".parse().unwrap();
    println!("llm-gateway listening on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();

    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .unwrap();
}

async fn shutdown_signal() {
    let _ = signal::ctrl_c().await;
}
