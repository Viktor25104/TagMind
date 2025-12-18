package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"os"
	"strings"
	"time"
)

type TagCommandPayload struct {
	ContactID string `json:"contactId"`
	Tag       string `json:"tag"`
	Count     *int   `json:"count,omitempty"`
	Payload   string `json:"payload,omitempty"`
	Locale    string `json:"locale,omitempty"`
	Text      string `json:"text,omitempty"`
}

type TagCommandResponse struct {
	RequestID string `json:"requestId"`
	Decision  string `json:"decision"`
	ReplyText string `json:"replyText"`
	ContactID string `json:"contactId"`
	SessionID string `json:"sessionId"`
	Tag       string `json:"tag"`
}

type orchestratorClient struct {
	httpClient *http.Client
	tagURL     string
}

func newOrchestratorClient() *orchestratorClient {
	tagURL := strings.TrimSpace(os.Getenv("ORCHESTRATOR_TAG_URL"))
	if tagURL == "" {
		base := strings.TrimSpace(os.Getenv("ORCHESTRATOR_URL"))
		if base == "" {
			base = "http://orchestrator-api:8082"
		}
		tagURL = strings.TrimSuffix(base, "/") + "/v1/conversations/tag"
	}

	return &orchestratorClient{
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
			Transport: &http.Transport{
				Proxy:               http.ProxyFromEnvironment,
				DialContext:         (&net.Dialer{Timeout: 3 * time.Second}).DialContext,
				TLSHandshakeTimeout: 3 * time.Second,
			},
		},
		tagURL: tagURL,
	}
}

func (c *orchestratorClient) sendTagRequest(ctx context.Context, requestID string, payload TagCommandPayload) (*TagCommandResponse, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("marshal tag payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.tagURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Request-Id", requestID)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("orchestrator request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		var errResp map[string]any
		_ = json.NewDecoder(resp.Body).Decode(&errResp)
		message := "unexpected orchestrator error"
		if m, ok := errResp["message"].(string); ok && m != "" {
			message = m
		}
		return nil, fmt.Errorf("orchestrator error status=%d message=%s", resp.StatusCode, message)
	}

	var tagResp TagCommandResponse
	if err := json.NewDecoder(resp.Body).Decode(&tagResp); err != nil {
		return nil, fmt.Errorf("decode orchestrator response: %w", err)
	}
	if tagResp.ReplyText == "" && !strings.EqualFold(tagResp.Decision, "DO_NOT_RESPOND") {
		return nil, errors.New("orchestrator response missing replyText")
	}
	return &tagResp, nil
}
