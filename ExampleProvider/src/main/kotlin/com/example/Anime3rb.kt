package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Anime3rb : MainAPI() {
    override var mainUrl = "https://anime3rb.com"
    override var name = "Anime3rb"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "$mainUrl/search?q=" to "بحث",
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val res = app.get(url)
        val doc = res.document
        
        // محاولة البحث بأكثر من طريقة لضمان التقاط النتائج
        return doc.select("div.col-6.col-md-2, div.anime-card-container, div.mb-3").mapNotNull {
            val title = it.select("h3, a.title, div.title, h5").text().trim()
            val href = it.select("a").attr("href")
            val posterUrl = it.select("img").attr("src")
            
            if(href.isEmpty() || title.isEmpty()) return@mapNotNull null

            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(posterUrl)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("h1.title, h1").text().trim()
        val poster = doc.select("img.poster, div.poster img, img.img-fluid").attr("src")
        val description = doc.select("div.story, p.synopsis, div.card-body p").text()

        // استخدام newEpisode المتوافق مع التحديث الأخير
        val episodes = doc.select("div.episodes-list a, div.episodes-card a, div.col-6 a").map {
            val epName = it.text().trim()
            val epUrl = it.attr("href")
            // استخراج الرقم بذكاء من النص
            val epNum = Regex("(\\d+)").find(epName)?.value?.toIntOrNull()

            newEpisode(fixUrl(epUrl)) {
                this.name = epName
                this.episode = epNum
            }
        }

        return newAnimeLoadResponse(title, url
