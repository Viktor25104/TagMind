package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
)

type AckResponse struct {
	RequestID string `json:"requestId"`
	OK        bool   `json:"ok"`
	Ignored   bool   `json:"ignored,omitempty"`
	Decision  string `json:"decision,omitempty"`
	ReplyText string `json:"replyText,omitempty"`
}

type DevMessageRequest struct {
	UserID string `json:"userId"`
	ChatID string `json:"chatId"`
	Text   string `json:"text"`
	Locale string `json:"locale,omitempty"`
}

type DevMessageResponse struct {
	RequestID string `json:"requestId"`
	ContactID string `json:"contactId"`
	Answer    string `json:"answer"`
	Decision  string `json:"decision"`
}

type telegramChatMessage struct {
	Text    string           `json:"text"`
	Caption string           `json:"caption"`
	Chat    *telegramChatRef `json:"chat"`
}

type telegramUpdate struct {
	Message           *telegramChatMessage `json:"message"`
	EditedMessage     *telegramChatMessage `json:"edited_message"`
	ChannelPost       *telegramChatMessage `json:"channel_post"`
	EditedChannelPost *telegramChatMessage `json:"edited_channel_post"`
}

type telegramChatRef struct {
	ID   int64  `json:"id"`
	Type string `json:"type"`
}

func (u telegramUpdate) text() string {
	if msg := u.primaryMessage(); msg != nil {
		if t := strings.TrimSpace(msg.Text); t != "" {
			return t
		}
		if c := strings.TrimSpace(msg.Caption); c != "" {
			return c
		}
	}
	return ""
}

func (u telegramUpdate) primaryMessage() *telegramChatMessage {
	switch {
	case u.Message != nil:
		return u.Message
	case u.EditedMessage != nil:
		return u.EditedMessage
	case u.ChannelPost != nil:
		return u.ChannelPost
	case u.EditedChannelPost != nil:
		return u.EditedChannelPost
	default:
		return nil
	}
}

func (u telegramUpdate) chatID() string {
	if msg := u.primaryMessage(); msg != nil {
		return msg.chatID()
	}
	return ""
}

func (m *telegramChatMessage) chatID() string {
	if m == nil || m.Chat == nil {
		return ""
	}
	return strconv.FormatInt(m.Chat.ID, 10)
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
	orchestratorClient := newOrchestratorClient()

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

		var update telegramUpdate
		if err := json.Unmarshal(body, &update); err != nil {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   "Invalid JSON",
			})
			return
		}

		text := update.text()
		if text == "" {
			writeJSON(w, http.StatusOK, reqID, AckResponse{
				RequestID: reqID,
				OK:        true,
				Ignored:   true,
			})
			return
		}

		cmd, err := ParseTagCommand(text)
		if err != nil {
			writeJSON(w, http.StatusOK, reqID, AckResponse{
				RequestID: reqID,
				OK:        true,
				Ignored:   true,
			})
			return
		}

		chatID := update.chatID()
		if chatID == "" {
			writeJSON(w, http.StatusOK, reqID, AckResponse{
				RequestID: reqID,
				OK:        true,
				Ignored:   true,
			})
			return
		}

		contactID, err := deriveContactID(chatID)
		if err != nil {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   err.Error(),
			})
			return
		}

		tagResp, err := orchestratorClient.sendTagRequest(r.Context(), reqID, TagCommandPayload{
			ContactID: contactID,
			Tag:       cmd.Tag,
			Count:     cmd.Count,
			Payload:   cmd.Payload,
			Locale:    "ru-RU",
			Text:      text,
		})
		if err != nil {
			log.Printf("orchestrator tag call failed (webhook): %v", err)
			writeJSON(w, http.StatusBadGateway, reqID, map[string]any{
				"requestId": reqID,
				"code":      "ORCHESTRATOR_ERROR",
				"message":   "failed to call orchestrator",
			})
			return
		}

		writeJSON(w, http.StatusOK, reqID, AckResponse{
			RequestID: reqID,
			OK:        true,
			Decision:  tagResp.Decision,
			ReplyText: tagResp.ReplyText,
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

		cmd, err := ParseTagCommand(req.Text)
		if err != nil {
			writeJSON(w, http.StatusOK, reqID, AckResponse{
				RequestID: reqID,
				OK:        true,
				Ignored:   true,
			})
			return
		}

		contactID, err := deriveContactID(req.ChatID)
		if err != nil {
			writeJSON(w, http.StatusBadRequest, reqID, map[string]any{
				"requestId": reqID,
				"code":      "BAD_REQUEST",
				"message":   err.Error(),
			})
			return
		}

		locale := normalizeLocale(req.Locale)
		tagResp, err := orchestratorClient.sendTagRequest(r.Context(), reqID, TagCommandPayload{
			ContactID: contactID,
			Tag:       cmd.Tag,
			Count:     cmd.Count,
			Payload:   cmd.Payload,
			Locale:    locale,
			Text:      req.Text,
		})
		if err != nil {
			log.Printf("orchestrator tag call failed (dev): %v", err)
			writeJSON(w, http.StatusBadGateway, reqID, map[string]any{
				"requestId": reqID,
				"code":      "ORCHESTRATOR_ERROR",
				"message":   "failed to call orchestrator",
			})
			return
		}

		writeJSON(w, http.StatusOK, reqID, DevMessageResponse{
			RequestID: reqID,
			ContactID: contactID,
			Answer:    tagResp.ReplyText,
			Decision:  tagResp.Decision,
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

func normalizeLocale(locale string) string {
	trimmed := strings.TrimSpace(locale)
	if trimmed == "" {
		return "ru-RU"
	}
	return trimmed
}
