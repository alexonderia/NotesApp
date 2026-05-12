# NotesApp

Android-приложение для заметок с рукописным вводом на Jetpack Compose и распознаванием рукописи через ML Kit (русский язык).

## Что уже реализовано

- Список заметок и папок.
- Хранение заметок в файловом vault через SAF (`.md` + `.ink.json`), без Room.
- Редактор заметки с двумя режимами:
  - `Рукописный`
  - `Текст` (распознанный + ручные дописки).
- Распознавание рукописи:
  - `Распознать новое` (инкрементально, без дублей),
  - `Перераспознать всё`,
  - `Распознать по строкам` (через блоки).
- Панель инструментов рисования:
  - ручка/ластик,
  - выбор толщины (2/4/8/12),
  - выбор цвета (черный/синий/красный/зеленый),
  - undo/redo/clear.

## Текущая модель данных

### `Note`

Ключевые поля:
- `recognizedText`: зеркальный текст из рукописи.
- `manualText`: вручную дописанный текст (не меняется при `clearHandwriting`).
- `recognizedStrokeIds`: id штрихов, уже учтенных в `recognizedText`.
- `strokes`: все штрихи на холсте (`InkStroke`).
- `handwritingBlocks`: логические блоки/строки для positional recognition.

### `InkStroke`

- `id`, `points`, `color`, `width`, `timestamp`.
- `toolType: ToolType`:
  - `Pen`
  - `Eraser`

По обратной совместимости старые штрихи без `toolType` считаются `Pen`.

### `HandwritingBlock`

- `id`
- `strokeIds`
- `bounds: StrokeBounds`
- `recognizedText`
- `orderIndex`
- `updatedAt`

## Хранение на диске

Vault-структура:

- `notes/<noteId>.md` — метаданные и текстовые секции.
- `ink/<noteId>.ink.json` — штрихи и рукописные блоки.
- `metadata/folders.json` — папки.

### Формат `.ink.json` (version 2)

Содержит:
- `strokes` (включая `toolType`),
- `handwritingBlocks`.

### Совместимость

- Если в старом `ink.json` нет `handwritingBlocks`, читается `emptyList()`.
- Если у штриха нет `toolType`, он трактуется как `Pen`.
- Для старых `.md` без `recognizedStrokeIds` используется `emptySet()`.

## Логика распознавания

Распознавание выполняется только по `Pen`-штрихам.

### 1) `Распознать новое`

- Берутся штрихи, чьи id отсутствуют в `recognizedStrokeIds`.
- Распознается только эта дельта.
- Результат дописывается в `recognizedText`.
- Новые id добавляются в `recognizedStrokeIds`.

### 2) `Перераспознать всё`

- Распознаются все `Pen`-штрихи.
- `recognizedText` заменяется целиком.
- `recognizedStrokeIds` = все id `Pen`-штрихов.
- `handwritingBlocks` сбрасываются.

### 3) `Распознать по строкам`

- Через `HandwritingBlockBuilder` строятся строки/блоки по координатам:
  - группировка по близкому `Y`,
  - внутри строки сортировка по `X`,
  - строки сортируются сверху вниз.
- Каждый блок распознается отдельно.
- Итоговый `recognizedText` собирается по `orderIndex` через `"\n"`.

## Рукописные инструменты и поведение

### Pen
- Создает новый `InkStroke` с выбранным цветом/толщиной.
- После нового штриха очищается `redoStack`.

### Eraser (MVP stroke eraser)
- Не создает отдельные eraser-штрихи.
- При движении удаляет целиком те `Pen`-штрихи, что пересекаются/попадают в радиус ластика.
- После изменений очищается `redoStack`.

### Undo/Redo
- `Undo`: убирает последний штрих в `redoStack`.
- `Redo`: возвращает последний штрих из `redoStack`.
- После нового рисования/ластика redo-история очищается.

### Clear
- Очищает:
  - `strokes`,
  - `recognizedText`,
  - `recognizedStrokeIds`,
  - `handwritingBlocks`,
  - `redoStack` (во ViewModel).
- `manualText` сохраняется.

## Архитектура (кратко)

- UI: Compose-экраны (`features/*`).
- Состояние экрана: `ViewModel` + `StateFlow`.
- Домен/модели: `core/model`.
- Хранилище: `NotesRepository` + `SafFileNotesRepository`.
- Распознавание: `HandwritingRecognitionService` (ML Kit Digital Ink).
- Навигация: `AppNavHost`.

## Ограничения текущей версии

- Нет частичного стирания (только удаление whole-stroke).
- Нет lasso/shape/highlighter/zoom/pan/infinite canvas.
- Нет обратной синхронизации typed -> handwriting.
- Нет сложной NLP-склейки.

## Как собрать

Из корня проекта:

```powershell
.\gradlew.bat assembleDebug
```

## Быстрый ручной smoke-test

1. Создать заметку и перейти в `Рукописный`.
2. Нарисовать несколько штрихов разными цветами/толщинами.
3. Проверить `Undo`/`Redo`, затем ластик.
4. Нажать `Распознать новое`, затем дописать и снова `Распознать новое` (без дублей).
5. Написать две строки одна под другой и нажать `Распознать по строкам` (порядок сверху вниз в текстовом режиме).
6. Нажать `Clear` и убедиться, что рукопись/зеркало очищены, а `manualText` остался.
