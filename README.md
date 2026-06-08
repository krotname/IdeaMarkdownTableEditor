# Markdown Table Editor для JetBrains IDEs

Markdown Table Editor превращает IDE JetBrains на IntelliJ Platform в удобный редактор Markdown-таблиц.
Берёте чужую косую таблицу или сгенерированную ИИ, жмете `Tab`, а плагин выровняет колонки, сохранит Markdown-разметку
и поможет быстро переставлять строки, колонки и данные прямо в IDE.

## Другие версии

- Для Notepad++: [NppMarkdownTableEditor](https://github.com/krotname/NppMarkdownTableEditor)

## Демо

![Пример выравнивания Markdown-таблицы в JetBrains IDE](docs/demo.gif)

GIF собран из реальных скриншотов IDE JetBrains под Windows: открыт обычный `.md` файл, команда `Align Table` вызвана через `Ctrl+Alt+A`.

## Зачем он нужен

- Не нужно уходить из IDE JetBrains в отдельный Markdown-редактор только ради таблиц.
- Большие pipe-таблицы остаются читаемыми в plain text.
- `Tab`, сортировка и операции со строками/колонками экономят ручное выравнивание.
- CSV/TSV можно быстро превратить в аккуратную Markdown-таблицу.
- Команды доступны из меню `Tools`, контекстного меню редактора и поиска действий IDE.

## Возможности

- `Tab` внутри Markdown-таблицы выравнивает таблицу.
- Вне Markdown-таблицы `Tab` работает как обычный отступ IDE.
- Выравнивание таблицы вокруг курсора.
- Переход к следующей или предыдущей ячейке.
- Вставка, удаление и перемещение строк.
- Вставка, удаление и перемещение колонок.
- Сортировка строк по текущей колонке по возрастанию или убыванию.
- Конвертация выделенного CSV/TSV-текста или текущего CSV/TSV-блока в Markdown-таблицу.
- Вставка новой таблицы с выбранным числом колонок и строк.
- Сохранение Markdown-маркеров выравнивания: `---`, `:---`, `---:`, `:---:`.
- Корректная обработка escaped pipes: `\|`.

## Установка

1. Скачайте ZIP-архив из последнего релиза: https://github.com/krotname/IdeaMarkdownTableEditor/releases/latest
2. Откройте совместимую IDE JetBrains.
3. Перейдите в `Settings | Plugins`.
4. Нажмите на шестеренку и выберите `Install Plugin from Disk...`.
5. Выберите скачанный ZIP-файл.

Плагин собран как dynamic plugin и рассчитан на установку без перезапуска IDE в совместимых версиях продуктов JetBrains. Если сама IDE попросит перезапуск, значит платформа обнаружила ограничение загрузки или выгрузки в текущей сессии.

## Публикация

- Страница версий JetBrains Marketplace: https://plugins.jetbrains.com/plugin/32159-markdown-table-editor/edit/versions

## Команды

Команды доступны в меню `Tools > Markdown Table Editor` и в контекстном меню редактора.

| Команда                                        | Что делает                                                               |
| ---------------------------------------------- | ------------------------------------------------------------------------ |
| `Tab: Align Markdown Table`                    | Выравнивает таблицу под курсором; вне таблицы работает как обычный `Tab` |
| `Align Table`                                  | Выравнивает текущую Markdown-таблицу                                     |
| `Next Cell` / `Previous Cell`                  | Перемещает курсор между ячейками                                         |
| `Insert Row Below` / `Delete Row`              | Добавляет или удаляет строку                                             |
| `Insert Column Right` / `Delete Column`        | Добавляет или удаляет колонку                                            |
| `Move Row Up` / `Move Row Down`                | Перемещает текущую строку                                                |
| `Move Column Left` / `Move Column Right`       | Перемещает текущую колонку                                               |
| `Sort Rows Ascending` / `Sort Rows Descending` | Сортирует строки по текущей колонке                                      |
| `Convert CSV/TSV to Table`                     | Превращает выделенный CSV/TSV или текущий блок в Markdown-таблицу        |
| `Insert New Table`                             | Вставляет новую таблицу заданного размера                                |

Например, выделите `Name,Score` и следующую строку `Anna,10` или поставьте курсор внутрь такого блока.
Выполните `Tools > Markdown Table Editor > Convert CSV/TSV to Table`.
Получится Markdown-таблица с колонками `Name` и `Score`.

Горячие клавиши по умолчанию:

| Команда                     | Сочетание    |
| --------------------------- | ------------ |
| `Tab: Align Markdown Table` | `Tab`        |
| `Align Table`               | `Ctrl+Alt+A` |

Остальным командам можно назначить сочетания в `Settings | Keymap`.

## Сборка и тесты

Нужен JDK 21. IntelliJ Platform SDK для сборки скачивается Gradle IntelliJ Platform plugin.

```powershell
.\gradlew.bat check buildPlugin
```

Готовый ZIP появится в папке `build/distributions`.
Если локально установлена IDE JetBrains и не хочется ждать скачивания платформы, можно передать путь:

```powershell
.\gradlew.bat check buildPlugin -PplatformLocalPath="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3"
```

Для Marketplace-проверки совместимости:

```powershell
.\gradlew.bat verifyPlugin
```

В GitHub Actions verifier дополнительно берет recommended IDEs. Локально это можно включить явно:

```powershell
.\gradlew.bat verifyPlugin -PverifyRecommendedIdes=true
```

Полный релизный build:

```powershell
.\gradlew.bat clean check verifyPlugin buildPlugin
```

Для быстрой локальной сборки без Plugin Verifier:

```powershell
.\gradlew.bat clean check buildPlugin
```

Для локальной установки используйте готовый ZIP из `build/distributions` через `Settings | Plugins | Install Plugin from Disk...`.

## Лицензия

Код плагина распространяется по лицензии MIT. Полный текст лицензии находится в [LICENSE](LICENSE).
Сторонние build-инструменты перечислены в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
