package main

import "testing"

func TestDeriveContactID(t *testing.T) {
	got, err := deriveContactID(" chat_123 ")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "tg:chat_123" {
		t.Fatalf("expected tg:chat_123, got %s", got)
	}
}

func TestDeriveContactIDEmpty(t *testing.T) {
	if _, err := deriveContactID("   "); err != ErrInvalidChatID {
		t.Fatalf("expected ErrInvalidChatID, got %v", err)
	}
}
