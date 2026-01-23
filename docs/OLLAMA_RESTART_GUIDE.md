# Как перезапустить Ollama на сервере

## Проблема: "address already in use"

Если при попытке запустить `ollama serve` вы видите ошибку "bind: address already in use", это означает, что процесс Ollama все еще работает.

## Решение

### Шаг 1: Найти процесс Ollama

```bash
# Вариант 1: По имени процесса
ps aux | grep ollama

# Вариант 2: По порту
sudo lsof -i :11434
# или
sudo netstat -tlnp | grep 11434
# или
sudo ss -tlnp | grep 11434
```

### Шаг 2: Остановить процесс

```bash
# Если видите PID в выводе выше, используйте:
kill <PID>

# Или более агрессивно:
kill -9 <PID>

# Или остановить все процессы ollama:
pkill -9 ollama

# Или если запущен как systemd сервис:
sudo systemctl stop ollama
```

### Шаг 3: Проверить, что порт свободен

```bash
# Должно вернуть пустой результат
sudo lsof -i :11434

# Или
sudo netstat -tlnp | grep 11434
```

### Шаг 4: Запустить Ollama снова

```bash
export OLLAMA_HOST=0.0.0.0:11434
ollama serve &
```

### Шаг 5: Проверить, что запустился

```bash
# Проверить процессы
ps aux | grep ollama

# Проверить порт
sudo netstat -tlnp | grep 11434
# Должно показать: 0.0.0.0:11434

# Проверить API
curl http://localhost:11434/api/version
curl http://localhost:11434/api/tags
```

## Полная последовательность команд

```bash
# 1. Найти и остановить все процессы Ollama
pkill -9 ollama
sudo systemctl stop ollama 2>/dev/null

# 2. Подождать секунду
sleep 1

# 3. Проверить, что порт свободен
sudo lsof -i :11434 || echo "Порт свободен"

# 4. Запустить Ollama
export OLLAMA_HOST=0.0.0.0:11434
ollama serve &

# 5. Подождать запуска
sleep 2

# 6. Проверить
curl http://localhost:11434/api/version
curl http://localhost:11434/api/tags
```

## Если процесс автоматически перезапускается

Если после `kill` процесс сразу появляется снова, значит он управляется systemd или другим менеджером процессов.

### Решение для systemd

```bash
# 1. Проверить статус сервиса
sudo systemctl status ollama

# 2. Остановить сервис


# 3. Отключить автозапуск (чтобы не запускался автоматически)
sudo systemctl disable ollama

# 4. Убедиться, что процесс остановлен
sudo pkill -9 ollama
sleep 1
sudo lsof -i :11434
# Должно быть пусто

# 5. Настроить systemd для внешнего доступа
sudo mkdir -p /etc/systemd/system/ollama.service.d/
sudo tee /etc/systemd/system/ollama.service.d/override.conf > /dev/null <<EOF
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
EOF

# 6. Перезагрузить конфигурацию и запустить
sudo systemctl daemon-reload
sudo systemctl enable ollama
sudo systemctl start ollama

# 7. Проверить
sudo systemctl status ollama
sudo lsof -i :11434
# Должно показать: 0.0.0.0:11434
```

### Или запустить вручную (без systemd)

```bash
# 1. Остановить systemd сервис
sudo systemctl stop ollama
sudo systemctl disable ollama

# 2. Убить все процессы
sudo pkill -9 ollama
sleep 2

# 3. Проверить, что порт свободен
sudo lsof -i :11434

# 4. Запустить вручную в фоне
export OLLAMA_HOST=0.0.0.0:11434
nohup ollama serve > /tmp/ollama.log 2>&1 &

# 5. Проверить
sleep 3
ps aux | grep ollama
sudo lsof -i :11434
curl http://localhost:11434/api/version
```

### Проверьте другие процессы

Возможно, порт занят другим приложением:

```bash
sudo lsof -i :11434
```

### Используйте другой порт (временно)

Если нужно запустить Ollama на другом порту для теста:

```bash
export OLLAMA_HOST=0.0.0.0:11435
ollama serve &
```

Но тогда нужно будет обращаться по новому порту: `http://193.42.127.171:11435`
