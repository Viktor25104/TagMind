FROM golang:1.22-alpine AS build
WORKDIR /src
COPY services/tg-gateway/stub/go.mod .
COPY services/tg-gateway/stub/main.go .
RUN go build -o /out/tg-gateway main.go

FROM alpine:3.20
RUN apk add --no-cache ca-certificates wget
COPY --from=build /out/tg-gateway /usr/local/bin/tg-gateway
EXPOSE 8081
ENTRYPOINT ["tg-gateway"]
