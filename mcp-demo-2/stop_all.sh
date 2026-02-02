#!/bin/bash

echo "🛑 MCP + CAMARA POC Durduruluyor..."

# PID dosyasından process'leri durdur
PID_FILE=".pids"

if [ -f "$PID_FILE" ]; then
    while read pid; do
        if ps -p $pid > /dev/null 2>&1; then
            kill $pid 2>/dev/null
            echo "Process $pid durduruldu"
        fi
    done < $PID_FILE
    rm $PID_FILE
fi

# Port bazlı temizlik (eğer PID dosyası yoksa)
echo "Port bazlı temizlik yapılıyor..."

# Port 5003 (AI Agent)
lsof -ti:5003 | xargs kill -9 2>/dev/null

# Port 5001 (MCP Server)
lsof -ti:5001 | xargs kill -9 2>/dev/null

# Port 5002 (CAMARA API)
lsof -ti:5002 | xargs kill -9 2>/dev/null

# Port 3000 (User UI)
lsof -ti:3000 | xargs kill -9 2>/dev/null

echo ""
echo "✅ Tüm servisler durduruldu!"
