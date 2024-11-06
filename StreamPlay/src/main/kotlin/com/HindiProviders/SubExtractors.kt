package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.coroutines.*

const val stremioAPI = "https://opensubtitles-v3.strem.io"
const val wyzieSubsAPI = "https://subs.wyzie.ru"
const val watchSomuchAPI = "https://watchsomuch.tv"

object SubExtractors : ViStreamProvider() {
    suspend fun invokeStremio(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val slug = if(season == null) {
            "movie/$imdbId"
        } else {
            "series/$imdbId:$season:$episode"
        }
        app.get("$stremioAPI/subtitles/$slug.json").parsedSafe<OsResult>()?.subtitles?.map { sub ->
            val lang = SubtitleHelper.fromThreeLettersToLanguage(sub.lang ?: "") ?: sub.lang
            if (lang!!.startsWith("vi") || lang!!.startsWith("Vietnamese")) {
                subtitleCallback.invoke(
                    SubtitleFile("Vietnamese Stremio", sub.url ?: return@map)
                )
            } else {
                null
            }
        }
    }

    suspend fun invokeWatchsomuch(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val id = imdbId?.removePrefix("tt")
        val epsId = app.post(
            "$watchSomuchAPI/Watch/ajMovieTorrents.aspx",
            data = mapOf(
                "index" to "0",
                "mid" to "$id",
                "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45",
                "lid" to "",
                "liu" to ""
            ),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { eps ->
            if (season == null) {
                eps.firstOrNull()?.id
            } else {
                eps.find { it.episode == episode && it.season == season }?.id
            }
        } ?: return
        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)
        val subUrl = if (season == null) {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part="
        } else {
            "$watchSomuchAPI/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$epsId&part=S${seasonSlug}E${episodeSlug}"
        }
        app.get(subUrl).parsedSafe<WatchsomuchSubResponses>()?.subtitles?.map { sub ->
            if (sub.label!!.startsWith("Vietnamese") || sub.label!!.startsWith("vi")) {
                val response = app.get(fixUrl(sub.url?: return@map null, "$watchSomuchAPI")).text
                if (!response.contains("404")) {
                    subtitleCallback.invoke(
                        SubtitleFile("Vietnamese watchSomuch", fixUrl(sub.url?: return@map null, "$watchSomuchAPI"))
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun invokeWYZIESubs(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val url = if (season != null && episode != null) "${wyzieSubsAPI}/search?id=${imdbId}&season=${season}&episode=${episode}" else "${wyzieSubsAPI}/search?id=${imdbId}" 
        val json = app.get(url).text
        val data = parseJson<ArrayList<WYZIESubtitle>>(json)
        data.forEach {
            if (it.display!!.startsWith("vi") || it.display!!.startsWith("Vietnamese")) {
                subtitleCallback.invoke(
                    SubtitleFile("Vietnamese WYZIE", it.url)
                )
            } else {
                null
            }
        }
    }
}
