package com.falconfx.gtfsviewer.data.db.repo

import com.falconfx.gtfsviewer.R

data class LoadingProgress(
    val phase: Phase,
    val percentage: Int
) {
    enum class Phase {
        DOWNLOADING, CLEARING, EXTRACTING,
        PARSING_STOPS, PARSING_ROUTES, PARSING_TRIPS, PARSING_TIMETABLE,
        COMPLETE
    }

    companion object {
        // Weight distribution based on observed timings
        const val DOWNLOAD_WEIGHT = 5
        const val CLEAR_WEIGHT = 15
        const val EXTRACT_WEIGHT = 10
        const val STOPS_WEIGHT = 5
        const val ROUTES_WEIGHT = 5
        const val TRIPS_WEIGHT = 10
        const val TIMETABLE_WEIGHT = 50

        fun calculating(phase: Phase, phaseProgress: Float): LoadingProgress {
            val basePercentage = when (phase) {
                Phase.DOWNLOADING -> 0
                Phase.CLEARING -> DOWNLOAD_WEIGHT
                Phase.EXTRACTING -> DOWNLOAD_WEIGHT + CLEAR_WEIGHT
                Phase.PARSING_STOPS -> DOWNLOAD_WEIGHT + CLEAR_WEIGHT + EXTRACT_WEIGHT
                Phase.PARSING_ROUTES -> DOWNLOAD_WEIGHT + CLEAR_WEIGHT + EXTRACT_WEIGHT + STOPS_WEIGHT
                Phase.PARSING_TRIPS -> DOWNLOAD_WEIGHT + CLEAR_WEIGHT + EXTRACT_WEIGHT + STOPS_WEIGHT + ROUTES_WEIGHT
                Phase.PARSING_TIMETABLE -> DOWNLOAD_WEIGHT + CLEAR_WEIGHT + EXTRACT_WEIGHT + STOPS_WEIGHT + ROUTES_WEIGHT + TRIPS_WEIGHT
                Phase.COMPLETE -> 100
            }

            val phaseWeight = when (phase) {
                Phase.DOWNLOADING -> DOWNLOAD_WEIGHT
                Phase.CLEARING -> CLEAR_WEIGHT
                Phase.EXTRACTING -> EXTRACT_WEIGHT
                Phase.PARSING_STOPS -> STOPS_WEIGHT
                Phase.PARSING_ROUTES -> ROUTES_WEIGHT
                Phase.PARSING_TRIPS -> TRIPS_WEIGHT
                Phase.PARSING_TIMETABLE -> TIMETABLE_WEIGHT
                Phase.COMPLETE -> 0
            }

            val percentage = (basePercentage + (phaseProgress * phaseWeight)).toInt().coerceIn(0, 100)

            return LoadingProgress(
                phase = phase,
                percentage = percentage
            )
        }

        fun Phase.getMessageResId(): Int = when (this) {
            Phase.DOWNLOADING -> R.string.progress_downloading
            Phase.CLEARING -> R.string.progress_clearing
            Phase.EXTRACTING -> R.string.progress_extracting
            Phase.PARSING_STOPS -> R.string.progress_parsing_stops
            Phase.PARSING_ROUTES -> R.string.progress_parsing_routes
            Phase.PARSING_TRIPS -> R.string.progress_parsing_trips
            Phase.PARSING_TIMETABLE -> R.string.progress_parsing_timetable
            Phase.COMPLETE -> R.string.progress_complete
        }
    }
}
