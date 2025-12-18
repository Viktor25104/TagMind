package main

import (
	"errors"
	"testing"
)

func mustPtr(v int) *int {
	return &v
}

func TestParseTagCommand(t *testing.T) {
	tests := []struct {
		name    string
		input   string
		want    *TagCommand
		wantErr error
	}{
		{
			name:  "llm with payload",
			input: "@tagmind llm: hello world",
			want: &TagCommand{
				Tag:     "llm",
				Payload: "hello world",
			},
		},
		{
			name:  "help uppercase prefix",
			input: "   @TAGMIND help:",
			want: &TagCommand{
				Tag: "help",
			},
		},
		{
			name:  "recap with count",
			input: "@tagmind recap[12]:",
			want: &TagCommand{
				Tag:   "recap",
				Count: mustPtr(12),
			},
		},
		{
			name:  "judge count and payload",
			input: "@tagmind judge[3]: check arguments",
			want: &TagCommand{
				Tag:     "judge",
				Count:   mustPtr(3),
				Payload: "check arguments",
			},
		},
		{
			name:  "fix without colon",
			input: "@tagmind fix[5] refine wording please",
			want: &TagCommand{
				Tag:     "fix",
				Count:   mustPtr(5),
				Payload: "refine wording please",
			},
		},
		{
			name:  "plan without payload",
			input: "@tagmind plan",
			want: &TagCommand{
				Tag: "plan",
			},
		},
		{
			name:    "missing prefix",
			input:   "hello world",
			wantErr: ErrNoTagmindPrefix,
		},
		{
			name:    "missing tag",
			input:   "@tagmind  : hi",
			wantErr: ErrMissingTag,
		},
		{
			name:    "unknown tag",
			input:   "@tagmind unknown: hi",
			wantErr: ErrUnknownTag,
		},
		{
			name:    "invalid count",
			input:   "@tagmind recap[-1]: hi",
			wantErr: ErrInvalidCount,
		},
		{
			name:    "invalid count without closing",
			input:   "@tagmind recap[10 hi",
			wantErr: ErrInvalidCount,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseTagCommand(tt.input)
			if tt.wantErr != nil {
				if !errors.Is(err, tt.wantErr) {
					t.Fatalf("expected error %v, got %v", tt.wantErr, err)
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if got.Tag != tt.want.Tag {
				t.Fatalf("tag mismatch: want %s, got %s", tt.want.Tag, got.Tag)
			}

			if tt.want.Count == nil {
				if got.Count != nil {
					t.Fatalf("expected nil count, got %d", *got.Count)
				}
			} else {
				if got.Count == nil || *got.Count != *tt.want.Count {
					t.Fatalf("count mismatch: want %d, got %v", *tt.want.Count, got.Count)
				}
			}

			if got.Payload != tt.want.Payload {
				t.Fatalf("payload mismatch: want %q, got %q", tt.want.Payload, got.Payload)
			}
		})
	}
}
