#!/bin/bash
# .env yüklenip uygulama çalıştırılır. Önce: cp env.example .env  ve  .env içine GEMINI_API_KEY yazın.

set -e
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Hata: .env bulunamadı. Önce: cp env.example .env"
  echo "Ardından .env içine GEMINI_API_KEY değerini yazın."
  exit 1
fi

set -a
source .env
set +a

if [ -z "${GEMINI_API_KEY}" ] || [ "${GEMINI_API_KEY}" = "your-gemini-api-key-here" ]; then
  echo "Hata: GEMINI_API_KEY .env içinde tanımlı değil veya placeholder. Gerçek key girin."
  exit 1
fi

exec mvn spring-boot:run
