FROM rust:1.82-slim AS build
WORKDIR /src
COPY services/llm-gateway/stub/Cargo.toml Cargo.toml
COPY services/llm-gateway/stub/src src
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates wget && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/release/llm-gateway /usr/local/bin/llm-gateway
EXPOSE 8084
ENTRYPOINT ["llm-gateway"]
