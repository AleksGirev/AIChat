# Быстрая настройка Ollama для удаленного доступа

## Проблема
Ollama слушает только на `127.0.0.1:11434` (localhost), поэтому недоступен извне.

## Быстрое решение

### Шаг 1: Остановить текущий процесс Ollama
```bash
pkill ollama
# или если systemd:
sudo systemctl stop ollama
```

### Шаг 2: Запустить с внешним доступом

**Вариант A (рекомендуется):** Слушать на всех интерфейсах
```bash
export OLLAMA_HOST=0.0.0.0:11434
ollama serve &
```

**Вариант B:** Слушать на конкретном IP
```bash
export OLLAMA_HOST=193.42.127.171:11434
ollama serve &
```

> **Примечание:** `0.0.0.0` означает "все интерфейсы", что более гибко. Конкретный IP тоже работает, но привязывает сервер к одному интерфейсу.

### Шаг 3: Проверить
```bash
# Проверить, что слушает на всех интерфейсах
sudo netstat -tlnp | grep 11434
# Должно показать: 0.0.0.0:11434

# Проверить доступность
curl http://localhost:11434/api/tags
```

### Шаг 4: Открыть порт в файрволе (если используется)
```bash
# UFW
sudo ufw allow 11434/tcp

# firewalld
sudo firewall-cmd --permanent --add-port=11434/tcp && sudo firewall-cmd --reload
```

## Постоянная настройка (systemd)

Если Ollama запущен как сервис:

```bash
# Создать override конфигурацию
sudo mkdir -p /etc/systemd/system/ollama.service.d/
sudo tee /etc/systemd/system/ollama.service.d/override.conf > /dev/null <<EOF
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
EOF

# Применить изменения
sudo systemctl daemon-reload
sudo systemctl restart ollama
sudo systemctl status ollama
```

## Загрузка модели

После настройки доступа загрузите нужную модель на сервере:

```bash
# На сервере (в другом терминале или после остановки serve)
ollama pull qwen2:7b-instruct

# Проверить список загруженных моделей
ollama list

# Проверить доступность модели через API
curl http://193.42.127.171:11434/api/tags
```

> **Важно:** Загрузка модели может занять время (зависит от размера модели и скорости интернета). Для `qwen2:7b-instruct` это примерно 4-5 ГБ.

## Проверка извне

После настройки проверьте с вашего компьютера:

```bash
# Список моделей
curl http://193.42.127.171:11434/api/tags

# Тест генерации
curl http://193.42.127.171:11434/api/generate -d '{"model": "qwen2:7b-instruct", "prompt": "Привет!", "stream": false}'

# Или используйте скрипт проверки из проекта
./scripts/check-ollama-remote.sh http://193.42.127.171:11434 qwen2:7b-instruct
```
