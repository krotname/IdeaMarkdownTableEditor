# Исправление и проверка Markdown Table Editor для JetBrains

Изменение Java core проверяется вместе с C++ и TypeScript. Исправление диапазона
редактора проверяется отдельно: перевод строки после выделенной таблицы и все
последующие пустые строки должны сохраняться. Ранбук основан на общем аудите
03.10.2026 и дополняет [документацию библиотеки](../core/README.md).

## Где исправлять

- `core/src/main/java/name/krot/markdowntable/core/MarkdownTableEngine.java` —
  грамматика CSV/TSV, форматирование и общие операции таблицы.
- Код плагина — выделение диапазона, команды документа и интеграция JetBrains.
- `core/src/test/resources/markdown-table-core-golden.json` — общий контракт.

Минимальный вход сначала воспроизвести до правки. При изменении общего поведения
перенести regression-case в одинаковые JSON Npp и VS Code, затем выполнить тесты
всех трёх портов. Пути, команда семантического сравнения и матрица пограничных
случаев — в [общем ранбуке Npp](https://github.com/krotname/NppMarkdownTableEditor/blob/master/docs/RUNBOOK.md).

## Уроки CSV/TSV и адаптера

1. Сохранять пустые первые/последние ячейки и полностью пустые TSV-записи.
2. При неровном заголовке учитывать структуру body. Кавычки, запятые и TAB внутри
   значений не заменяют выбранную грамматику.
3. Считать логические записи: physical lines внутри quoted CSV-поля не являются
   отдельными строками TSV. Не вводить безусловный выбор CSV по quoted TAB.
4. Проверять LF, CRLF и CR при замене выделенного диапазона; сохранять **весь**
   конечный суффикс, а не один перевод строки.
5. Собирать длинный суффикс линейно. Конкатенация растущей строки в цикле создаёт
   квадратичную работу на большом выделении с пустыми строками.

Паритет ядра не проверяет корректность диапазона JetBrains. Для adapter-багов
нужен отдельный вход с таблицей, соседним текстом, выделением и ожидаемым суффиксом.

## Локальные команды

Запускать из корня репозитория. Использовать Gradle Wrapper и установленный JDK;
Java toolchain проекта — 17. Путь `JAVA_HOME` должен указывать на существующий JDK,
а не переноситься вслепую с другой машины.

Проверка core, покрытия и артефактов Maven Central без загрузки:

```powershell
.\gradlew.bat :core:test :core:check :core:corePerformance --console=plain
```

`:core:check` включает JaCoCo verification и `verifyCentralPublication`.
Показать отдельно только проверку публикационных файлов можно так:

```powershell
.\gradlew.bat :core:verifyCentralPublication --console=plain
```

Отчёты: `core/build/reports/coverage/jacoco.xml` и
`core/build/reports/coverage/html`. Процент patch coverage из Codecov и gate
проекта — разные показатели; актуальный порог берётся из `core/build.gradle.kts`.
Не подменять полезный regression-case тестом, который повторяет реализацию.

Для изменений интеграции плагина:

```powershell
.\gradlew.bat check buildPlugin --console=plain
.\gradlew.bat verifyPlugin --console=plain
```

При сбое различать недоступный IDE download, неверный JDK/cwd, ошибку core и
реальную несовместимость Plugin Verifier. После успешной целевой проверки
повторять её только при новой правке или конкретной нерешённой проблеме.

## Версии и публикация

Версия плагина читается из `VERSION`, версия библиотеки — из `core/VERSION`.
`verifyCentralPublication` ничего не публикует. Merge bugfix не означает выход
новой версии на Maven Central или JetBrains Marketplace. Перед релизом следовать
[core release](../core/README.md#maven-central-release),
[Marketplace](../MARKETPLACE_SUBMISSION.md) и согласовать версии связанных
плагинов по release-процедуре. Опубликованные версии immutable.

## PR и проверенный результат

Ветка `feature/…` от свежего `origin/main`; затем целевые проверки, commit/push/PR,
CI финального head, применимое ревью, merge, проверки merge SHA и синхронизация
своего checkout. Для docs-only diff внешнее ревью не запускать; текущие path filters
могут не запускать build CI. Отсутствующий run не считать зелёным run.
Общий порядок и команды GitHub — в [ранбуке Npp](https://github.com/krotname/NppMarkdownTableEditor/blob/master/docs/RUNBOOK.md#pr-ci-и-завершение).

Отсутствие checks также проверять через `gh api repos/krotname/IdeaMarkdownTableEditor/actions/workflows`.
На 04.10.2026 workflows имеют `disabled_manually` во время
[миграции CI #63](https://github.com/krotname/IdeaMarkdownTableEditor/pull/63).
Docs PR сохраняется до успешных required checks; включение остановленных workflows
и снятие gates не являются частью обновления документации. Snapshot перечитать
перед продолжением, не принимать его за постоянное состояние CI.

В аудите 03.10.2026 core tests, coverage gate, publication verification и
performance прошли. CSV benchmark: 276 ms при лимите 800 ms; TSV: 285 ms при
лимите 900 ms. Это исторические измерения, а не обещание скорости на любой машине.
Исправления: [#58](https://github.com/krotname/IdeaMarkdownTableEditor/pull/58),
[#60](https://github.com/krotname/IdeaMarkdownTableEditor/pull/60),
[#61](https://github.com/krotname/IdeaMarkdownTableEditor/pull/61),
[#62](https://github.com/krotname/IdeaMarkdownTableEditor/pull/62).

Сопровождение сайта и выкладки:
[source](https://github.com/krotname/MarkdownTableEditorSite/blob/main/docs/RUNBOOK.md),
[ProdOps](https://github.com/krotname/ProdOps/blob/main/docs/MARKDOWN-TABLE-EDITOR-RUNBOOK.md).
