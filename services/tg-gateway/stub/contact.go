package main

import (
	"errors"
	"strings"
)

var ErrInvalidChatID = errors.New("chatId must be provided for contactId derivation")

// deriveContactID trims leading and trailing whitespace from chatID and returns a contact identifier prefixed with "tg:".
// If chatID is empty after trimming, it returns ErrInvalidChatID.
func deriveContactID(chatID string) (string, error) {
	trimmed := strings.TrimSpace(chatID)
	if trimmed == "" {
		return "", ErrInvalidChatID
	}
	return "tg:" + trimmed, nil
}