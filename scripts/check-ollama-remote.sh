#!/bin/bash

# Скрипт для проверки доступности удаленного Ollama сервера
# Использование: ./check-ollama-remote.sh [URL] [MODEL]

OLLAMA_URL="${1:-http://193.42.127.171:11434}"
MODEL="${2:-qwen2:7b-instruct}"

echo "🔍 Проверка Ollama сервера"
echo "================================"
echo "URL: $OLLAMA_URL"
echo "Модель: $MODEL"
echo "================================"
echo ""

# Функция для проверки эндпоинта
check_endpoint() {
    local endpoint=$1
    local description=$2
    
    echo -n "Проверка $description... "
    
    response=$(curl -s -w "\n%{http_code}" -m 5 "$OLLAMA_URL$endpoint" 2>&1)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ] || [ "$http_code" = "000" ]; then
        if [ "$http_code" = "200" ]; then
            echo "✅ OK (HTTP $http_code)"
            if [ -n "$body" ] && [ "$body" != "null" ]; then
                echo "   Ответ: $(echo "$body" | head -c 100)..."
            fi
        else
            echo "❌ Недоступен (Connection refused)"
            echo "   Возможные причины:"
            echo "   - Ollama не запущен"
            echo "   - Слушает только на localhost (127.0.0.1)"
            echo "   - Порт закрыт файрволом"
        fi
    else
        echo "⚠️  HTTP $http_code"
        if [ -n "$body" ]; then
            echo "   Ответ: $body"
        fi
    fi
    echo ""
}

# 1. Проверка базового доступа
echo "1. Проверка доступности сервера..."
if ping -c 1 -W 2 $(echo $OLLAMA_URL | sed 's|http://||' | sed 's|:.*||') > /dev/null 2>&1; then
    echo "   ✅ Хост доступен (ping)"
else
    echo "   ⚠️  Хост не отвечает на ping (может быть отключен ICMP)"
fi
echo ""

# 2. Проверка /api/version
check_endpoint "/api/version" "/api/version"

# 3. Проверка /api/tags (список моделей)
echo "3. Проверка списка моделей..."
echo -n "   /api/tags... "

tags_response=$(curl -s -w "\n%{http_code}" -m 5 "$OLLAMA_URL/api/tags" 2>&1)
tags_http_code=$(echo "$tags_response" | tail -n1)
tags_body=$(echo "$tags_response" | sed '$d')

if [ "$tags_http_code" = "200" ]; then
    echo "✅ OK (HTTP $tags_http_code)"
    # Парсим список моделей (более надежный способ)
    if echo "$tags_body" | grep -q '"models":\[\]' || ! echo "$tags_body" | grep -q '"name"'; then
        echo "   ⚠️  Модели не найдены (список пуст)"
        echo "   Выполните на сервере: ollama pull $MODEL"
    else
        # Извлекаем имена моделей
        model_names=$(echo "$tags_body" | grep -o '"name":"[^"]*"' | sed 's/"name":"//g' | sed 's/"//g')
        model_count=$(echo "$model_names" | grep -c . || echo "0")
        if [ "$model_count" = "0" ]; then
            echo "   ⚠️  Модели не найдены"
            echo "   Выполните на сервере: ollama pull $MODEL"
        else
            echo "   ✅ Найдено моделей: $model_count"
            echo "   Доступные модели: $(echo "$model_names" | tr '\n' ' ')"
        fi
    fi
elif [ "$tags_http_code" = "000" ]; then
    echo "❌ Недоступен"
else
    echo "⚠️  HTTP $tags_http_code"
fi
echo ""

# 4. Проверка /api/generate
echo "4. Проверка генерации текста..."
echo -n "   Тест /api/generate с моделью $MODEL... "

generate_response=$(curl -s -w "\n%{http_code}" -m 30 \
    -X POST \
    -H "Content-Type: application/json" \
    -d "{\"model\": \"$MODEL\", \"prompt\": \"Привет! Ответь одним словом: работает?\", \"stream\": false}" \
    "$OLLAMA_URL/api/generate" 2>&1)

http_code=$(echo "$generate_response" | tail -n1)
body=$(echo "$generate_response" | sed '$d')

if [ "$http_code" = "200" ]; then
    echo "✅ OK"
    # Извлечь ответ из JSON (простой парсинг)
    response_text=$(echo "$body" | grep -o '"response":"[^"]*"' | head -1 | sed 's/"response":"//' | sed 's/"$//')
    if [ -n "$response_text" ]; then
        echo "   Ответ модели: $response_text"
    fi
elif [ "$http_code" = "000" ]; then
    echo "❌ Недоступен"
    echo "   Ollama не отвечает на запросы"
else
    echo "⚠️  HTTP $http_code"
    if [ -n "$body" ]; then
        error_msg=$(echo "$body" | grep -o '"error":"[^"]*"' | head -1 | sed 's/"error":"//' | sed 's/"$//')
        if [ -n "$error_msg" ]; then
            echo "   Ошибка: $error_msg"
        else
            echo "   Ответ: $(echo "$body" | head -c 200)"
        fi
    fi
fi
echo ""

# Итоговая информация
echo "================================"
echo "Итоги проверки:"
echo ""

# Определяем статус
if [ "$tags_http_code" = "200" ] && [ "$http_code" = "200" ]; then
    echo "✅ Сервер полностью настроен и работает!"
elif [ "$tags_http_code" = "200" ] && [ "$http_code" != "200" ]; then
    echo "⚠️  Сервер доступен, но модель '$MODEL' не найдена"
    echo ""
    echo "Выполните на сервере для загрузки модели:"
    echo "  ollama pull $MODEL"
elif [ "$tags_http_code" = "000" ] || [ "$http_code" = "000" ]; then
    echo "❌ Сервер недоступен извне"
    echo ""
    echo "Выполните на сервере для настройки внешнего доступа:"
    echo "  export OLLAMA_HOST=0.0.0.0:11434"
    echo "  pkill ollama"
    echo "  ollama serve &"
    echo ""
    echo "Или для systemd:"
    echo "  sudo systemctl edit ollama"
    echo "  # Добавить: Environment=\"OLLAMA_HOST=0.0.0.0:11434\""
    echo "  sudo systemctl restart ollama"
else
    echo "⚠️  Обнаружены проблемы с конфигурацией"
fi

echo ""
echo "Подробная документация: docs/OLLAMA_REMOTE_SETUP.md"
echo "================================"
