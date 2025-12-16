package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"strings"
	"time"
)

type AckResponse struct {
	RequestID string `json:"requestId"`
	OK        bool   `json:"ok"`
}

type DevMessageRequest struct {
	UserID string `json:"userId"`
	ChatID string `json:"chatId"`
	Text   string `json:"text"`
	Locale string `json:"locale,omitempty"`
}

type DevMessageResponse struct {
	RequestID string `json:"requestId"`
	Answer    string `json:"answer"`
}

func newRequestID() string {
	b := make([]byte, 12)
	if _, err := rand.Read(b); err == nil {
		return "req_" + hex.EncodeToString(b)
	}
	return "req_" + time.Now().Format("20060102150405.000000000")
}

func getOrCreateRequestID(r *http.Request) string {
	id := strings.TrimSpace(r.Header.Get("X-Request-Id"))
	if len(id) >= 8 && len(id) <= 128 {
		return id
	}
	return newRequestID()
}

func writeJSON(w http.ResponseWriter, status int, requestID string, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("X-Request-Id", requestID)
	w.WriteHeader(status)
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		log.Printf("json encode error: %v", err)
	}
}

func writeText(w http.ResponseWriter, status int, requestID string, s string) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Header().Set("X-Request-Id", requestID)
	w.WriteHeader(status)
	if _, err := io.WriteString(w, s+"\n"); err != nil {
		log.Printf("write error: %v", err)
	}
}

func main() {
	mux := http.NewServeMux()

	// Health
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		reqID := getOrCreateRequestID(r)
		writeText(w, http.StatusOK, reqID, "ok")
	})

	// Telegram webhook receiver (raw JSON accepted)
	mux.HandleFunc("/v1/tg/webhook", func(w http.ResponseWriter, r *http.Request) {
		reqID := getOrCreateRequestID(r)

		if r.Method != http.MethodPost {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   "Method must be POST",
			})
			return
		}

		// Read body but do not validate schema strictly (Telegram update is flexible).
		body, err := io.ReadAll(io.LimitReader(r.Body, 2<<20)) // 2MB
		if err != nil {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   "Failed to read body",
			})
			return
		}

		// Just ensure it's valid JSON.
		var anyJSON any
		if err := json.Unmarshal(body, &anyJSON); err != nil {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   "Invalid JSON",
			})
			return
		}

		// Phase 4: no orchestration call yet. Just ACK.
		writeJSON(w, http.StatusOK, reqID, AckResponse{
			RequestID: reqID,
			OK:        true,
		})
	})

	// Dev endpoint to simulate message (no Telegram required)
	mux.HandleFunc("/v1/tg/dev/message", func(w http.ResponseWriter, r *http.Request) {
		reqID := getOrCreateRequestID(r)

		if r.Method != http.MethodPost {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   "Method must be POST",
			})
			return
		}

		var req DevMessageRequest
		dec := json.NewDecoder(io.LimitReader(r.Body, 512<<10)) // 512KB
		dec.DisallowUnknownFields()
		if err := dec.Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   "Invalid JSON body",
			})
			return
		}

		if strings.TrimSpace(req.UserID) == "" || strings.TrimSpace(req.ChatID) == "" || strings.TrimSpace(req.Text) == "" {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   "userId, chatId and text are required",
			})
			return
		}

		// Phase 4 mock answer
		writeJSON(w, http.StatusOK, reqID, DevMessageResponse{
			RequestID: reqID,
			Answer:    "stub: message received; orchestration will be added in a later commit",
		})
	})

	// Default
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		reqID := getOrCreateRequestID(r)
		writeText(w, http.StatusOK, reqID, "tg-gateway stub")
	})

	addr := ":8081"
	log.Printf("tg-gateway listening on %s", addr)
	log.Fatal(http.ListenAndServe(addr, mux))
}
