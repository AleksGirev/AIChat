# Vegetarian Recipes Chat

Консольное приложение для генерации вегетарианских рецептов с использованием локального LLM.

## Особенности

- ✅ **Системный промпт**: Автоматически применяется для всех запросов
- ✅ **Вегетарианская диета**: Разрешены яйца, рыба, молочные продукты
- ✅ **Запрещено**: Мясо и птица
- ✅ **Команда `recipe`**: Оптимизированный режим для быстрой и точной генерации рецептов
- ✅ **Оптимизированные параметры**: Temperature=0.3, Max tokens=300, Top-p=0.9

## Быстрый старт

### Запуск

```bash
# Через команду console-agent
console-agent offline

# Или через Gradle
./gradlew :console-agent:runOfflineChat
```

### Использование

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
AI (Recipe Mode): [оптимизированный ответ с рецептом]

You: What can I cook with eggs?
AI: [ответ с вегетарианским системным промптом]
```

## Команды

### `help`
Показать справку по всем доступным командам.

### `recipe <запрос>`
Генерация рецепта в оптимизированном режиме.

**Параметры:**
- Temperature: 0.3 (более детерминированный)
- Max tokens: 300 (быстрый и точный ответ)
- Top-p: 0.9 (сбалансированная креативность)
- System prompt: Вегетарианский промпт

**Примеры:**
```
recipe gluten-free dinner for two
recipe healthy breakfast with eggs
recipe fish dinner with vegetables
recipe dessert for 4 people
```

### `info`
Показать информацию о сервере и модели.

### `exit` / `quit`
Выйти из приложения.

## Диетические правила

### ✅ Разрешено
- Яйца
- Рыба и морепродукты
- Молочные продукты (молоко, сыр, йогурт, масло)
- Овощи, фрукты
- Зерновые, бобовые
- Орехи, семена

### ❌ Запрещено
- Мясо (говядина, свинина, баранина и т.д.)
- Птица (курица, индейка, утка и т.д.)

## Формат ответа

При использовании команды `recipe` ответ будет в структурированном формате:

```markdown
# Recipe Name
## Ingredients (exact measurements)
- Item 1: X g/ml
- Item 2: Y g/ml

## Instructions
1. Step one...
2. Step two...

## Nutrition (per serving)
- Calories: XXX kcal
- Protein: XX g
- Carbohydrates: XX g
- Fat: XX g

## Dietary Notes
- Vegetarian verified: Contains [eggs/fish/dairy] - no meat or poultry
```

## Оптимизированные параметры

Команда `recipe` использует следующие параметры для быстрой и точной генерации:

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| Temperature | 0.3 | Низкая температура для более детерминированных и точных ответов |
| Max tokens | 300 | Ограничение длины для быстрой генерации и фокусированного ответа |
| Top-p | 0.9 | Сбалансированная креативность при сохранении качества |
| System prompt | Вегетарианский | Обеспечивает соблюдение диетических правил и структурированный формат |

## Примеры использования

### Пример 1: Быстрый рецепт

```
You: recipe quick lunch
AI (Recipe Mode): 
# Quick Vegetarian Lunch Bowl

## Ingredients (exact measurements)
- Quinoa, cooked: 200 g
- Chickpeas, canned: 150 g
- Cherry tomatoes: 100 g
- Feta cheese: 50 g
- Olive oil: 15 ml
- Lemon juice: 10 ml
...
```

### Пример 2: Обычный чат

```
You: What are good sources of protein for vegetarians?
AI: [ответ с учетом вегетарианского промпта, но без строгого формата рецепта]
```

## Конфигурация

### Переменные окружения

```bash
# Кастомный сервер
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2:7b-instruct

console-agent offline
```

### Изменение параметров

Параметры можно изменить в файле `VegetarianRecipePrompt.kt`:

```kotlin
const val OPTIMAL_TEMPERATURE = 0.3
const val OPTIMAL_MAX_TOKENS = 300
const val OPTIMAL_TOP_P = 0.9
```

## Требования

- Ollama запущен локально: `ollama serve`
- Модель загружена: `ollama pull qwen2:7b-instruct`
- Java Runtime для запуска через Gradle

## Устранение проблем

### Ollama не отвечает

```bash
# Проверить статус
curl http://localhost:11434/api/version

# Запустить Ollama
ollama serve
```

### Модель не найдена

```bash
# Загрузить модель
ollama pull qwen2:7b-instruct

# Проверить список
ollama list
```

### Медленные ответы

- Команда `recipe` оптимизирована для быстрых ответов (300 токенов)
- Обычный чат может быть медленнее из-за отсутствия ограничений
