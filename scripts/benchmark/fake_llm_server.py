"""A fake OpenAI-compatible model server on the loopback address, for benchmarks that need no real model.

    python3 -I scripts/benchmark/fake_llm_server.py PORT LOG_FILE

Answers every POST /v1/chat/completions with one fixed, well-formed reply and appends one JSON line per request
to LOG_FILE with the sizes of what it received (characters of the system and user messages, and their sum). It
records sizes only, never message text. Used by benchmark plan P2 (input budget): the system talks to it through
its normal local-model adapter (provider `local`), so every request the system would send to a model is measured.
Standard library only; binds to 127.0.0.1.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

REPLY = "Dear sender,\n\nThank you for your email. It will be looked at.\n\nBest regards,"


def main():
    port, log_path = int(sys.argv[1]), sys.argv[2]

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            messages = body.get("messages", [])
            system = sum(len(m.get("content", "")) for m in messages if m.get("role") == "system")
            user = sum(len(m.get("content", "")) for m in messages if m.get("role") == "user")
            with open(log_path, "a", encoding="utf-8") as log:
                log.write(json.dumps({"system": system, "user": user, "total": system + user,
                                      "model": body.get("model")}) + "\n")
            answer = json.dumps({"choices": [{"message": {"role": "assistant", "content": REPLY}}]}).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(answer)))
            self.end_headers()
            self.wfile.write(answer)

    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
