package main

import (
	"errors"
	"strconv"
	"strings"
)

var (
	ErrNoTagmindPrefix = errors.New("message must start with @tagmind")
	ErrMissingTag      = errors.New("tag is required")
	ErrUnknownTag      = errors.New("unsupported tag")
	ErrInvalidCount    = errors.New("invalid [n] value")
)

const tagmindPrefix = "@tagmind"

var supportedTags = map[string]struct{}{
	"help":  {},
	"llm":   {},
	"web":   {},
	"recap": {},
	"judge": {},
	"fix":   {},
	"plan":  {},
	"safe":  {},
}

type TagCommand struct {
	Tag     string
	Count   *int
	Payload string
}

// ParseTagCommand parses an input string that begins with the "@tagmind" prefix into a TagCommand.
// It validates the prefix, extracts the tag (lowercased), an optional bracketed count `[n]`, and the remaining payload.
// Returns ErrNoTagmindPrefix, ErrMissingTag, ErrUnknownTag, or ErrInvalidCount for the corresponding validation failures.
func ParseTagCommand(input string) (*TagCommand, error) {
	trimmed := strings.TrimSpace(input)
	if trimmed == "" {
		return nil, ErrNoTagmindPrefix
	}

	if len(trimmed) < len(tagmindPrefix) || !strings.EqualFold(trimmed[:len(tagmindPrefix)], tagmindPrefix) {
		return nil, ErrNoTagmindPrefix
	}

	rest := strings.TrimLeft(trimmed[len(tagmindPrefix):], " \t")
	if rest == "" {
		return nil, ErrMissingTag
	}

	tag, remainder, err := parseTag(rest)
	if err != nil {
		return nil, err
	}

	if _, ok := supportedTags[tag]; !ok {
		return nil, ErrUnknownTag
	}

	count, payload, err := parseCountAndPayload(remainder)
	if err != nil {
		return nil, err
	}

	return &TagCommand{
		Tag:     tag,
		Count:   count,
		Payload: payload,
	}, nil
}

// parseTag extracts a contiguous sequence of ASCII letters from the start of input
// and returns that sequence lowercased along with the remaining suffix.
// If the input does not begin with a letter, it returns ErrMissingTag.
func parseTag(input string) (string, string, error) {
	end := 0
	for end < len(input) {
		c := input[end]
		if c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' {
			end++
			continue
		}
		break
	}

	if end == 0 {
		return "", "", ErrMissingTag
	}

	tag := strings.ToLower(input[:end])
	return tag, input[end:], nil
}

// parseCountAndPayload parses an optional bracketed positive integer count and the remaining payload from input.
// If input begins with `[n]` where `n` is one or more ASCII digits greater than zero, the function returns a pointer
// to that integer and the payload that follows. If no bracketed count is present, it returns a nil count pointer and
// the entire trimmed input as the payload. Leading whitespace is ignored and a single optional colon immediately after
// the count (or at the start if no count) is removed before trimming the payload.
// Returns ErrInvalidCount when the bracketed count is malformed: missing closing `]`, empty content, non-digit characters,
// or a value that cannot be parsed as an integer greater than zero.
func parseCountAndPayload(input string) (*int, string, error) {
	rest := strings.TrimLeft(input, " \t")
	var countPtr *int

	if strings.HasPrefix(rest, "[") {
		endIdx := strings.IndexByte(rest, ']')
		if endIdx == -1 {
			return nil, "", ErrInvalidCount
		}

		raw := strings.TrimSpace(rest[1:endIdx])
		if raw == "" {
			return nil, "", ErrInvalidCount
		}

		for _, ch := range raw {
			if ch < '0' || ch > '9' {
				return nil, "", ErrInvalidCount
			}
		}

		value, err := strconv.Atoi(raw)
		if err != nil || value <= 0 {
			return nil, "", ErrInvalidCount
		}

		countPtr = new(int)
		*countPtr = value
		rest = rest[endIdx+1:]
	} else {
		rest = input
	}

	rest = strings.TrimLeft(rest, " \t")
	if strings.HasPrefix(rest, ":") {
		rest = rest[1:]
	}

	payload := strings.TrimSpace(rest)
	return countPtr, payload, nil
}