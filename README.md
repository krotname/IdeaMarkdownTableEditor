# Markdown Table Editor for IntelliJ IDEA

## Русский

Markdown Table Editor помогает быстро приводить Markdown-таблицы в порядок прямо в редакторе JetBrains IDE.

Он нужен, когда таблица в README, заметках или документации разъехалась после правки текста. Вместо ручного добавления пробелов достаточно поставить курсор внутрь таблицы и нажать `Tab`.

Что умеет плагин:

- выравнивает Markdown-таблицу по `Tab`;
- не мешает обычному `Tab` вне настоящих Markdown-таблиц;
- переходит между ячейками;
- добавляет и удаляет строки и столбцы;
- перемещает строки и столбцы;
- сохраняет выравнивание колонок: left, center, right.

### Установка

Если у вас есть готовый ZIP-файл плагина:

1. Откройте IntelliJ IDEA.
2. Перейдите в `Settings | Plugins`.
3. Нажмите на шестеренку и выберите `Install Plugin from Disk...`.
4. Выберите `MarkdownTableEditorIdea-0.1.0.zip`.
5. Перезапустите IDE.

Для локальной сборки и установки:

```powershell
.\build.ps1
.\install.ps1
```

Готовый ZIP создается в папке `build`.

## English

Markdown Table Editor keeps Markdown pipe tables clean without breaking your writing flow.

It is useful when a table in a README, changelog, issue template, or project note becomes hard to read after a quick edit. Put the caret inside the table, press `Tab`, and the plugin aligns it for you.

Features:

- align a Markdown table with `Tab`;
- keep normal `Tab` behavior outside real Markdown tables;
- move between cells;
- insert and delete rows and columns;
- move rows and columns;
- preserve column alignment: left, center, right.

### Installation

If you already have the plugin ZIP:

1. Open IntelliJ IDEA.
2. Go to `Settings | Plugins`.
3. Click the gear icon and choose `Install Plugin from Disk...`.
4. Select `MarkdownTableEditorIdea-0.1.0.zip`.
5. Restart the IDE.

To build and install locally:

```powershell
.\build.ps1
.\install.ps1
```

The plugin ZIP is created in the `build` directory.
