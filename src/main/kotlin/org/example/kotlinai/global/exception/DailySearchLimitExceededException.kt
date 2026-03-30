package org.example.kotlinai.global.exception

class DailySearchLimitExceededException(
    message: String = "일일 검색 횟수(5회)를 초과했습니다. 내일 다시 시도해주세요.",
) : RuntimeException(message)