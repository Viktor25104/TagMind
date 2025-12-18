package main

import (
	"errors"
	"strings"
)

var ErrInvalidChatID = errors.New("chatId must be provided for contactId derivation")

func deriveContactID(chatID string) (string, error) {
	trimmed := strings.TrimSpace(chatID)
	if trimmed == "" {
		return "", ErrInvalidChatID
	}
	return "tg:" + trimmed, nil
}
