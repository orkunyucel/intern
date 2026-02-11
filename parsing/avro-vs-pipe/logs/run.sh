#!/bin/bash
# log.html dosyasını localhost'ta açar
PORT=${1:-8080}
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$SCRIPT_DIR/runs"
LATEST=$(ls -t "$LOG_DIR" 2>/dev/null | head -1)

if [ -z "$LATEST" ]; then
  echo "Hata: runs/ altinda log bulunamadi."
  exit 1
fi

LOG_FILE="$LOG_DIR/$LATEST/log.html"
if [ ! -f "$LOG_FILE" ]; then
  echo "Hata: $LOG_FILE bulunamadi."
  exit 1
fi

echo "Serving: $LOG_FILE"
echo "URL:     http://localhost:$PORT/runs/$LATEST/log.html"
echo "Ctrl+C ile kapat."
cd "$SCRIPT_DIR"
python3 -m http.server "$PORT"
