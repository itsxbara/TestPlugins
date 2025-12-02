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
        
        // البحث عن الروابط التي تحتوي على titles كما في الموقع
        return doc.select("a[href*='/titles/']").mapNotNull {
            val href = it.attr("href")
            val container = it.parent()
            
            // محاولة التقاط الصورة من العنصر نفسه أو الأب
            val posterUrl = it.select("img").attr("src").ifEmpty { 
                container?.select("img")?.attr("src") ?: "" 
            }
            val title = it.text().ifEmpty { 
                container?.select("h3, .title")?.text() ?: "انمي بدون عنوان" 
            }
            
            if(posterUrl.isEmpty()) return@mapNotNull null

            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(posterUrl)
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("h1").text().trim()
        val poster = doc.select("img[class*='object-cover']").attr("src")
        val description = doc.select("p.font-light, div.story").text()

        // استخراج الحلقات بدقة بناءً على كود HTML الذي أرسلته
        val episodes = doc.select("a[href*='/episode/']").mapNotNull {
            val href = it.attr("href")
            val videoData = it.select("div.video-data")
            val epName = videoData.select("span").text()
            val epNum = Regex("(\\d+)").find(epName)?.value?.toIntOrNull()
            
            newEpisode(fixUrl(href)) {
                this.name = epName
                this.episode = epNum
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrl(poster)
            this.plot = description
            // إضافة DubStatus.Sub إجبارية في التحديث الجديد
            addEpisodes(DubStatus.Sub, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // البحث عن روابط التحميل المباشرة
        doc.select("a[href*='/download/']").forEach { link ->
            val downloadUrl = link.attr("href")
            val text = link.text()
            
            // استخدام newExtractorLink لتجنب الخطأ الأحمر
            callback.invoke(
                newExtractorLink(
                    source = "Anime3rb Direct",
                    name = text,
                    url = downloadUrl,
                    referer = mainUrl,
                    quality = getQualityFromName(text)
                )
            )
        }

        // استخراج السيرفرات (Iframe)
        doc.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            
            // الترتيب الصحيح: (الرابط، الترجمة، الرد) لتجنب Type Mismatch
            loadExtractor(src, subtitleCallback, callback)
        }

        return true
    }
}
}
