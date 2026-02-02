#!/bin/bash

# MCP CAMARA POC - Start All Services
# This script starts all microservices in the correct order and opens the UI

set -e

echo "=================================================="
echo "MCP CAMARA POC - Starting All Services"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"

echo "Base directory: $BASE_DIR"
echo ""

# Load .env file if it exists
if [ -f "$BASE_DIR/.env" ]; then
    echo -e "${BLUE}Loading environment variables from .env file...${NC}"
    set -a # automatically export all variables
    source "$BASE_DIR/.env"
    set +a
    echo -e "${GREEN}Environment variables loaded.${NC}"
else
    echo -e "${YELLOW}Warning: .env file not found at $BASE_DIR/.env${NC}"
fi

# UI URL
UI_URL="http://localhost:8081"

# Function to wait for service
wait_for_service() {
    local port=$1
    local name=$2
    local health_path=${3:-"/actuator/health"}
    local max_attempts=30
    local attempt=1

    echo -n "Waiting for $name (port $port)"
    while [ $attempt -le $max_attempts ]; do
        if curl -s "http://localhost:$port$health_path" > /dev/null 2>&1; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    echo -e " ${RED}FAILED${NC}"
    return 1
}

# Function to open browser (cross-platform)
open_browser() {
    local url=$1
    echo -e "${BLUE}Opening UI in browser: $url${NC}"
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        open "$url"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        xdg-open "$url" 2>/dev/null || sensible-browser "$url" 2>/dev/null || echo "Please open $url manually"
    elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        # Windows
        start "$url"
    else
        echo "Please open $url manually"
    fi
}

# Build all modules first
echo -e "${YELLOW}Building all modules...${NC}"
cd "$BASE_DIR"
mvn clean install -DskipTests -q

# Create logs directory
mkdir -p "$BASE_DIR/logs"

# Start services in order (bottom to top of the stack)
# Port assignments (from application.yml):
# - network-mock-service: 8085
# - camara-service: 8084
# - mcp-server-service: 8083
# - llm-service: 8082
# - ai-agent-service: 8081

echo ""
echo -e "${YELLOW}Starting network-mock-service (port 8085)...${NC}"
cd "$BASE_DIR/services/network-mock-service"
mvn spring-boot:run > "$BASE_DIR/logs/network-mock-service.log" 2>&1 &
echo $! > "$BASE_DIR/logs/network-mock-service.pid"
wait_for_service 8085 "network-mock-service" "/camara/health"

echo ""
echo -e "${YELLOW}Starting camara-service (port 8084)...${NC}"
cd "$BASE_DIR/services/camara-service"
mvn spring-boot:run > "$BASE_DIR/logs/camara-service.log" 2>&1 &
echo $! > "$BASE_DIR/logs/camara-service.pid"
wait_for_service 8084 "camara-service" "/api/camara/health"

echo ""
echo -e "${YELLOW}Starting mcp-server-service (port 8083)...${NC}"
cd "$BASE_DIR/services/mcp-server-service"
mvn spring-boot:run > "$BASE_DIR/logs/mcp-server-service.log" 2>&1 &
echo $! > "$BASE_DIR/logs/mcp-server-service.pid"
wait_for_service 8083 "mcp-server-service" "/mcp/health"

echo ""
echo -e "${YELLOW}Starting llm-service (port 8082)...${NC}"
cd "$BASE_DIR/services/llm-service"
mvn spring-boot:run > "$BASE_DIR/logs/llm-service.log" 2>&1 &
echo $! > "$BASE_DIR/logs/llm-service.pid"
wait_for_service 8082 "llm-service" "/api/llm/health"

echo ""
echo -e "${YELLOW}Starting ai-agent-service (port 8081)...${NC}"
cd "$BASE_DIR/services/ai-agent-service"
mvn spring-boot:run > "$BASE_DIR/logs/ai-agent-service.log" 2>&1 &
echo $! > "$BASE_DIR/logs/ai-agent-service.pid"
wait_for_service 8081 "ai-agent-service" "/api/agent/health"

echo ""
echo "=================================================="
echo -e "${GREEN}All services started successfully!${NC}"
echo "=================================================="
echo ""
echo "Service URLs:"
echo "  - AI Agent:     http://localhost:8081/api/agent/health"
echo "  - LLM Service:  http://localhost:8082/api/llm/health"
echo "  - MCP Server:   http://localhost:8083/mcp/health"
echo "  - CAMARA:       http://localhost:8084/api/camara/health"
echo "  - Network Mock: http://localhost:8085/camara/health"
echo ""
echo -e "${GREEN}UI: $UI_URL${NC}"
echo ""
echo "Logs are in: $BASE_DIR/logs/"
echo "Stop all services with: ./scripts/stop-all.sh"
echo ""

# Open UI in browser
open_browser "$UI_URL"

