# Simple HTTP server for OpenRune jav config
import http.server
import socketserver
import os

PORT = 8765
DIRECTORY = os.path.join(os.path.expanduser("~"), "Documents", "OpenRune-Server", ".data")

class Handler(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        # Always serve from .data directory
        return os.path.join(DIRECTORY, path.lstrip('/'))
    
    def log_message(self, format, *args):
        pass  # Suppress logs

with socketserver.TCPServer(("", PORT), Handler) as httpd:
    print(f"Serving jav config on port {PORT}")
    httpd.serve_forever()
