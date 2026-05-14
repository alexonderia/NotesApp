package com.example.notesapp.core.model

/** Состояние распознавания одного логического блока/строки. */
enum class BlockRecognitionStatus {
    /** Ещё не обрабатывался (например, после загрузки старого файла). */
    Pending,

    /** Текст взят из предыдущего прогона без повторного вызова ML Kit. */
    Reused,

    /** Успешно распознан (возможно, через horizontal chunking). */
    Recognized,

    /** Ошибка ML Kit или пустой результат после fallback. */
    Failed,
    ;

    companion object {
        fun fromJson(raw: String?): BlockRecognitionStatus =
            when (raw?.lowercase()) {
                "reused" -> Reused
                "recognized" -> Recognized
                "failed" -> Failed
                else -> Pending
            }

        fun toJson(status: BlockRecognitionStatus): String =
            when (status) {
                Pending -> "pending"
                Reused -> "reused"
                Recognized -> "recognized"
                Failed -> "failed"
            }
    }
}
