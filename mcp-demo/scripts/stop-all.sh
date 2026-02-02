#!/bin/bash

# MCP CAMARA POC - Stop All Services

set -e

echo "=================================================="
echo "MCP CAMARA POC - Stopping All Services"
echo "=================================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

# Base directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

LOGS_DIR="$BASE_DIR/logs"

stop_service() {
    local name=$1
    local pid_file="$LOGS_DIR/$name.pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo -n "Stopping $name (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            sleep 2
            if kill -0 "$pid" 2>/dev/null; then
                kill -9 "$pid" 2>/dev/null || true
            fi
            echo -e " ${GREEN}stopped${NC}"
        else
            echo -e "$name ${RED}not running${NC}"
        fi
        rm -f "$pid_file"
    else
        echo -e "$name ${RED}no PID file${NC}"
    fi
}

# Stop services in reverse order
stop_service "ai-agent-service"
stop_service "llm-service"
stop_service "mcp-server-service"
stop_service "camara-service"
stop_service "network-mock-service"

# Kill any remaining Java processes on these ports
echo ""
echo "Cleaning up any remaining processes..."
for port in 8081 8082 8083 8084 8085; do
    pid=$(lsof -t -i:$port 2>/dev/null || true)
    if [ -n "$pid" ]; then
        echo "Killing process on port $port (PID: $pid)"
        kill -9 $pid 2>/dev/null || true
    fi
done

echo ""
echo -e "${GREEN}All services stopped.${NC}"
