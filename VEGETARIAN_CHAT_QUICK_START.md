# Быстрый старт: Вегетарианские рецепты

## Запуск

```bash
console-agent offline
```

## Основные команды

### `recipe <запрос>` - Генерация рецепта (оптимизированный режим)

**Примеры:**
```
recipe gluten-free dinner for two
recipe healthy breakfast with eggs
recipe fish dinner with vegetables
```

**Параметры:**
- Temperature: 0.3
- Max tokens: 300
- Top-p: 0.9
- System prompt: Вегетарианский

### `help` - Справка

### `info` - Информация о сервере

### `exit` - Выход

## Диетические правила

✅ **Разрешено:** Яйца, рыба, молочные продукты, овощи, фрукты, зерновые, бобовые, орехи, семена

❌ **Запрещено:** Мясо, птица

## Пример сессии

```
🧠 Offline AI Assistant - Vegetarian Recipes
============================================================
Server: http://localhost:11434
Model: qwen2:7b-instruct
Mode: Vegetarian recipes (eggs, fish, dairy allowed)
Type 'help' for commands, 'recipe' for recipe mode, 'exit' to quit
============================================================

✓ Connected to Ollama

You: recipe gluten-free dinner for two
AI (Recipe Mode): 
# Gluten-Free Vegetarian Dinner

## Ingredients (exact measurements)
- Salmon fillet: 300 g
- Quinoa: 150 g
- Broccoli: 200 g
- Olive oil: 30 ml
...

You: help
Available commands:
  help   - Show this help message
  recipe - Generate a vegetarian recipe (optimized mode)
  info   - Show server and model information
  exit   - Exit the chat

You: exit
Goodbye! 👋
```

## Подробная документация

См. `console-agent/VEGETARIAN_RECIPES_README.md`
