package com.mytune.app.data.api

import com.mytune.app.domain.Catalog
import com.mytune.app.domain.CollectionType
import com.mytune.app.domain.HomeData
import com.mytune.app.domain.HomeSection
import com.mytune.app.domain.MediaCollection
import com.mytune.app.domain.MusicRepository
import com.mytune.app.domain.SectionStyle
import com.mytune.app.domain.Song
import com.mytune.app.domain.UserPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.util.Calendar
import kotlin.random.Random

/** What the feed learns about the user's listening (see SettingsStore.recordPlay). */
data class TasteProfile(val topArtists: List<String> = emptyList(), val lastSong: Song? = null)

/**
 * Builds the personalised home feed out of many parallel searches, driven by the languages / artists the
 * user picked on onboarding plus what they actually listen to. Failed or empty rows are dropped.
 */
class HomeFeed(private val repo: MusicRepository) {

    private fun <T> Result<List<T>>.list(): List<T> = getOrNull().orEmpty()

    /** Round-robin merge so every source is represented. */
    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>()
        var i = 0
        while (lists.any { i < it.size }) { lists.forEach { l -> l.getOrNull(i)?.let(out::add) }; i++ }
        return out
    }

    suspend fun music(p: UserPrefs, taste: TasteProfile, seed: Long = System.currentTimeMillis()): Result<HomeData> = try {
        coroutineScope {
            val rnd = Random(seed)
            val langs = p.languages.ifEmpty { listOf("Hindi", "English") }
            val picked = p.artists.map { it.name }
            // Most played artists first, then the ones picked on onboarding.
            val favArtists = (taste.topArtists + picked).distinct().take(6)
            val topA = favArtists.take(3)

            // --- Just For You ---
            val jfyArtist: List<Deferred<List<Song>>> = favArtists.take(5).map { a -> async { repo.searchSongs(a, 12).list() } }
            val jfyBecause: Deferred<List<Song>>? = taste.lastSong?.let { s -> async { repo.suggestions(s.id, 15).list() } }
            val jfyLang = langs.take(3).map { l -> async { repo.searchSongs("${Catalog.q(l)} new songs", 10).list() } }

            // --- Other rows ---
            val trending = langs.take(4).map { l -> async { repo.searchSongs("trending ${Catalog.q(l)} songs", 12).list() } }
            val artistPlaylists = topA.map { a -> async { repo.searchPlaylists(a, 8).list() } }
            val albums = langs.take(4).map { l -> async { repo.searchAlbums("${Catalog.q(l)} new albums", 10).list() } }
            val charts = langs.take(4).map { l -> async { repo.searchPlaylists("top charts ${Catalog.q(l)}", 8).list() } }
            val langHits = langs.take(4).map { l -> l to async { repo.searchSongs("best of ${Catalog.q(l)} songs", 20).list() } }
            val moods = ApiConfig.HOME_MOODS.take(4).map { m -> async { repo.searchPlaylists("${Catalog.q(langs.first())} $m", 3).list() } }
            val timeQuery = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> "morning" to "Good Morning Picks"
                in 12..16 -> "afternoon feel good" to "Afternoon Vibes"
                in 17..21 -> "evening relax" to "Evening Wind Down"
                else -> "late night chill" to "Late Night Mood"
            }
            val timePl = async { repo.searchPlaylists("${Catalog.q(langs.first())} ${timeQuery.first}", 10).list() }
            val more = Catalog.artistsFor(langs).filter { it !in picked }.shuffled(rnd).take(8)
            val moreArtists = more.map { a -> async { repo.searchArtists(a, 1).list().firstOrNull() } }
            val countryTrending: Deferred<List<Song>>? =
                if (p.country != "India" && p.country != "Other") async { repo.searchSongs("top songs ${p.country}", 15).list() } else null

            val sections = mutableListOf<HomeSection>()

            // Just For You: shuffled a little each build, taste artists weigh most.
            val pool = interleave(jfyArtist.map { it.await() } + listOfNotNull(jfyBecause?.await()) + jfyLang.map { it.await() })
                .distinctBy { it.id }
            val jfy = (pool.take(10) + pool.drop(10).shuffled(rnd)).take(24)
            if (jfy.isNotEmpty()) sections += HomeSection("Just For You", songs = jfy, style = SectionStyle.HERO, subtitle = "Based on your taste")

            val because = taste.lastSong
            val becauseSongs = jfyBecause?.await().orEmpty().filter { it.id != because?.id }
            if (because != null && becauseSongs.size >= 4) {
                sections += HomeSection("Because you played ${because.title}", songs = becauseSongs.take(20))
            }

            val trend = interleave(trending.map { it.await() }).distinctBy { it.id }.take(20)
            if (trend.isNotEmpty()) sections += HomeSection("Trending Now", songs = trend, style = SectionStyle.RANKED)

            val chartList = interleave(charts.map { it.await() }).distinctBy { it.id }.take(16)
            if (chartList.isNotEmpty()) sections += HomeSection("Top Charts", collections = chartList, style = SectionStyle.TILES)

            val ap = interleave(artistPlaylists.map { it.await() }).distinctBy { it.id }.take(16)
            if (ap.isNotEmpty()) sections += HomeSection("Playlists From Your Artists", collections = ap, style = SectionStyle.TILES)

            val alb = interleave(albums.map { it.await() }).distinctBy { it.id }.take(16)
            if (alb.isNotEmpty()) sections += HomeSection("New Albums For You", collections = alb)

            countryTrending?.await()?.takeIf { it.isNotEmpty() }?.let { sections += HomeSection("Popular in ${p.country}", songs = it) }

            langHits.forEach { (l, d) ->
                d.await().takeIf { it.isNotEmpty() }?.let { sections += HomeSection("Best of $l", songs = it.shuffled(rnd)) }
            }

            timePl.await().takeIf { it.isNotEmpty() }?.let { sections += HomeSection(timeQuery.second, collections = it, style = SectionStyle.TILES) }

            val moodList = moods.flatMap { it.await() }.distinctBy { it.id }
            if (moodList.isNotEmpty()) sections += HomeSection("Made for Your Moods", collections = moodList, style = SectionStyle.TILES)

            val ma = moreArtists.mapNotNull { it.await() }.distinctBy { it.id }
            if (ma.isNotEmpty()) sections += HomeSection("Artists You May Like", collections = ma, circle = true)

            if (sections.isEmpty()) throw IOException("Network error")
            Result.success(HomeData(sections))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Podcasts tab. The API has no podcast endpoint, so shows and episodes are searched as songs, playlists
     * and albums using several keywords (in the user's languages too). Every row is optional.
     */
    suspend fun podcasts(p: UserPrefs): Result<HomeData> = try {
        coroutineScope {
            val langs = p.languages.ifEmpty { listOf("Hindi", "English") }
            val l0 = Catalog.q(langs.first())
            data class Row(val title: String, val songs: Deferred<List<Song>>? = null, val cols: Deferred<List<MediaCollection>>? = null)
            val rows = mutableListOf<Row>()
            rows += Row("Popular Podcasts", songs = async { repo.searchSongs("podcast", 20).list() }, cols = async { repo.searchAlbums("podcast", 15).list() })
            langs.take(3).forEach { l ->
                val q = Catalog.q(l)
                rows += Row("${l} Podcasts", songs = async { repo.searchSongs("$q podcast episode", 20).list() }, cols = async { repo.searchPlaylists("$q podcast", 15).list() })
            }
            rows += Row("Stories & Kahaniyan", songs = async { repo.searchSongs("$l0 kahani story", 20).list() }, cols = async { repo.searchPlaylists("$l0 stories", 15).list() })
            rows += Row("Motivation & Talks", songs = async { repo.searchSongs("motivational speech podcast", 20).list() })
            rows += Row("Comedy & Entertainment", songs = async { repo.searchSongs("$l0 comedy podcast", 20).list() })
            rows += Row("News & Knowledge", songs = async { repo.searchSongs("$l0 news podcast", 20).list() })
            rows += Row("Spiritual & Devotional Talks", songs = async { repo.searchSongs("pravachan spiritual talk", 20).list() })

            val sections = mutableListOf<HomeSection>()
            val seen = HashSet<String>()
            rows.forEach { r ->
                r.cols?.await()?.filter { seen.add("c" + it.id) }?.takeIf { it.isNotEmpty() }
                    ?.let { sections += HomeSection(r.title, collections = it, podcast = true, style = SectionStyle.TILES) }
                r.songs?.await()?.filter { seen.add("s" + it.id) }?.takeIf { it.isNotEmpty() }
                    ?.let { sections += HomeSection(if (r.cols == null) r.title else "${r.title} · Episodes", songs = it, podcast = true) }
            }
            if (sections.isEmpty()) throw IOException("No podcasts found")
            Result.success(HomeData(sections))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
