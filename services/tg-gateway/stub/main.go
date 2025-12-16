package main

import (
	"log"
	"net/http"
)

func writeLine(w http.ResponseWriter, s string) {
	if _, err := w.Write([]byte(s + "\n")); err != nil {
		log.Printf("write error: %v", err)
	}
}

func main() {
	mux := http.NewServeMux()

	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		writeLine(w, "ok")
	})

	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		writeLine(w, "tg-gateway stub")
	})

	addr := ":8081"
	log.Printf("tg-gateway listening on %s", addr)
	log.Fatal(http.ListenAndServe(addr, mux))
}
