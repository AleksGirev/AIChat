#!/bin/bash

# Скрипт для настройки Ollama на удаленном сервере для внешнего доступа
# Выполнить на сервере: bash setup-ollama-remote.sh

set -e

echo "🔧 Настройка Ollama для внешнего доступа"
echo "========================================"
echo ""

# 1. Создать директорию для override конфигурации
echo "1. Создание директории для конфигурации..."
sudo mkdir -p /etc/systemd/system/ollama.service.d/
echo "   ✅ Готово"

# 2. Создать файл конфигурации
echo "2. Создание конфигурации OLLAMA_HOST=0.0.0.0:11434..."
sudo tee /etc/systemd/system/ollama.service.d/override.conf > /dev/null <<EOF
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
EOF
echo "   ✅ Готово"

# 3. Перезагрузить конфигурацию systemd
echo "3. Перезагрузка конфигурации systemd..."
sudo systemctl daemon-reload
echo "   ✅ Готово"

# 4. Включить и запустить сервис
echo "4. Запуск сервиса Ollama..."
sudo systemctl enable ollama
sudo systemctl start ollama
echo "   ✅ Готово"

# 5. Подождать запуска
echo "5. Ожидание запуска сервиса..."
sleep 3

# 6. Проверить статус
echo ""
echo "6. Проверка статуса сервиса..."
sudo systemctl status ollama --no-pager -l | head -10

# 7. Проверить порт
echo ""
echo "7. Проверка порта 11434..."
PORT_CHECK=$(sudo lsof -i :11434 2>/dev/null | head -2)
if [ -n "$PORT_CHECK" ]; then
    echo "   ✅ Порт слушается:"
    echo "$PORT_CHECK" | sed 's/^/   /'
    
    # Проверить, слушает ли на 0.0.0.0
    if echo "$PORT_CHECK" | grep -q "0.0.0.0:11434\|:::11434"; then
        echo "   ✅ Сервер слушает на всех интерфейсах (0.0.0.0)"
    else
        echo "   ⚠️  Сервер слушает только на localhost"
    fi
else
    echo "   ❌ Порт не слушается"
fi

# 8. Проверить API
echo ""
echo "8. Проверка API..."
VERSION=$(curl -s http://localhost:11434/api/version 2>/dev/null || echo "ERROR")
if [ "$VERSION" != "ERROR" ]; then
    echo "   ✅ Версия: $VERSION"
else
    echo "   ❌ API не отвечает"
fi

TAGS=$(curl -s http://localhost:11434/api/tags 2>/dev/null || echo "ERROR")
if [ "$TAGS" != "ERROR" ]; then
    MODEL_COUNT=$(echo "$TAGS" | grep -o '"name"' | wc -l | tr -d ' ')
    if [ "$MODEL_COUNT" -gt 0 ]; then
        echo "   ✅ Найдено моделей: $MODEL_COUNT"
        MODEL_NAMES=$(echo "$TAGS" | grep -o '"name":"[^"]*"' | sed 's/"name":"//g' | sed 's/"//g' | tr '\n' ' ')
        echo "   Модели: $MODEL_NAMES"
    else
        echo "   ⚠️  Модели не найдены (список пуст)"
    fi
else
    echo "   ❌ Не удалось получить список моделей"
fi

echo ""
echo "========================================"
echo "✅ Настройка завершена!"
echo ""
echo "Проверьте доступность извне:"
echo "  curl http://193.42.127.171:11434/api/tags"
echo ""
