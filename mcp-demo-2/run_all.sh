#!/bin/bash

echo "🚀 MCP + CAMARA POC Başlatılıyor..."

# Renk kodları
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# PID dosyası
PID_FILE=".pids"
> $PID_FILE

# 1. CAMARA API
echo -e "${YELLOW}[1/4] CAMARA API başlatılıyor...${NC}"
cd camara-api
npm install --silent 2>/dev/null
node api.js &
echo $! >> ../$PID_FILE
cd ..
sleep 1

# 2. MCP Server
echo -e "${YELLOW}[2/4] MCP Server başlatılıyor...${NC}"
cd mcp-server
npm install --silent 2>/dev/null
node server.js &
echo $! >> ../$PID_FILE
cd ..
sleep 1

# 3. AI Agent
echo -e "${YELLOW}[3/4] AI Agent başlatılıyor...${NC}"
cd ai-agent
if [ ! -d "venv" ]; then
    echo "Virtual environment oluşturuluyor..."
    python3 -m venv venv
fi
source venv/bin/activate
pip install -r requirements.txt --quiet 2>/dev/null
python agent.py &
echo $! >> ../$PID_FILE
cd ..
sleep 1

# 4. User UI
echo -e "${YELLOW}[4/4] User UI başlatılıyor...${NC}"
cd user-ui
node server.js &
echo $! >> ../$PID_FILE
cd ..

echo ""
echo -e "${GREEN}✅ Tüm servisler başlatıldı!${NC}"
echo ""
echo "Portlar:"
echo "  - User UI:    http://localhost:3000"
echo "  - AI Agent:   http://localhost:5003"
echo "  - MCP Server: http://localhost:5001"
echo "  - CAMARA API: http://localhost:5002"
echo ""
echo "Tarayıcıda aç: http://localhost:3000"
echo "Durdurmak için: ./stop_all.sh"
echo ""

# Logları göster
wait
