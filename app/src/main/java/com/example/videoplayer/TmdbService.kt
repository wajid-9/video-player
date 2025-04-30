package com.example.videoplayer

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbService {
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "en-US",
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): TmdbResponse

    @GET("search/tv")
    suspend fun searchTvShows(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "en-US",
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): TmdbResponse

    @GET("tv/{seriesId}/season/{seasonNumber}/episode/{episodeNumber}")
    suspend fun getEpisode(
        @Path("seriesId") seriesId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Path("episodeNumber") episodeNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbEpisode

    @GET("tv/{seriesId}/season/{seasonNumber}")
    suspend fun getSeason(
        @Path("seriesId") seriesId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbSeason
}
data class TmdbResponse(
    val results: List<TmdbMovie> = emptyList()
)
data class TmdbMovie(
    @SerializedName("title") val movieTitle: String? = null,
    @SerializedName("name") val tvTitle: String? = null,
    var overview: String? = null,
    @SerializedName("release_date") val movieReleaseDate: String? = null,
    @SerializedName("first_air_date") val tvAirDate: String? = null,
    val vote_average: Float? = null,
    val poster_path: String? = null,
    @SerializedName("id") val id: Int = 0,
    val media_type: String? = null,
    var season: Int? = null,
    var episode: Int? = null
) :
    Parcelable {
    private var _displayTitle: String? = null

    var displayTitle: String
        get() = _displayTitle ?: tvTitle ?: movieTitle ?: "Unknown"
        set(value) {
            _displayTitle = value
        }

    var displayDate: String? = null
        get() = tvAirDate ?: movieReleaseDate

    val seriesId: Int?
        get() = if (media_type == "tv" || tvTitle != null) id else null

    constructor(parcel: Parcel) : this(
        movieTitle = parcel.readString(),
        tvTitle = parcel.readString(),
        overview = parcel.readString(),
        movieReleaseDate = parcel.readString(),
        tvAirDate = parcel.readString(),
        vote_average = parcel.readValue(Float::class.java.classLoader) as? Float,
        poster_path = parcel.readString(),
        id = parcel.readInt(),
        media_type = parcel.readString(),
        season = parcel.readValue(Int::class.java.classLoader) as? Int,
        episode = parcel.readValue(Int::class.java.classLoader) as? Int
    ) {
        _displayTitle = parcel.readString()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(movieTitle)
        parcel.writeString(tvTitle)
        parcel.writeString(overview)
        parcel.writeString(movieReleaseDate)
        parcel.writeString(tvAirDate)
        parcel.writeValue(vote_average)
        parcel.writeString(poster_path)
        parcel.writeInt(id)
        parcel.writeString(media_type)
        parcel.writeValue(season)
        parcel.writeValue(episode)
        parcel.writeString(_displayTitle)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TmdbMovie> {
        override fun createFromParcel(parcel: Parcel): TmdbMovie = TmdbMovie(parcel)
        override fun newArray(size: Int): Array<TmdbMovie?> = arrayOfNulls(size)
    }
}
data class TmdbEpisode(
    @SerializedName("name") val name: String?,
    val overview: String?,
    @SerializedName("air_date") val air_date: String?,
    val vote_average: Float?,
    @SerializedName("still_path") val still_path: String?,
    @SerializedName("episode_number") val episode_number: Int,
    @SerializedName("season_number") val season_number: Int
)

data class TmdbSeason(
    @SerializedName("poster_path") val poster_path: String?,
    @SerializedName("season_number") val season_number: Int,
    val overview: String?
)

object TmdbClient {
    private const val BASE_URL = "https://api.themoviedb.org/3/"
    private const val API_KEY = "f8a4820def9b2c491b5526997a764aa3"
    const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200"
    private const val TAG = "TmdbClient"

    val service: TmdbService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbService::class.java)
    }

    suspend fun getMediaData(query: String, isTvShow: Boolean): TmdbMovie? {
        if (query.isBlank()) {
            Log.w(TAG, "Empty query provided")
            return null
        }
        return try {
            Log.d(TAG, "Sending TMDB query: '$query' (isTvShow=$isTvShow)")
            val response = if (isTvShow) {
                service.searchTvShows(API_KEY, query)
            } else {
                service.searchMovies(API_KEY, query)
            }
            Log.d(TAG, "TMDB search returned ${response.results.size} results for '$query'")
            // Select the first result with a non-null poster_path
            val bestResult = response.results.firstOrNull { it.poster_path != null }
            if (bestResult != null) {
                Log.d(TAG, "Selected result: title=${bestResult.displayTitle}, id=${bestResult.id}, poster_path=${bestResult.poster_path}")
                return bestResult
            }
            Log.w(TAG, "No results with poster_path for '$query'")
            // Fallback: Retry without year for movies
            if (!isTvShow && query.contains("\\s\\d{4}$".toRegex())) {
                val queryWithoutYear = query.replace("\\s\\d{4}$".toRegex(), "").trim()
                Log.d(TAG, "Retrying TMDB query without year: '$queryWithoutYear'")
                val fallbackResponse = service.searchMovies(API_KEY, queryWithoutYear)
                val fallbackResult = fallbackResponse.results.firstOrNull { it.poster_path != null }
                if (fallbackResult != null) {
                    Log.d(TAG, "Fallback result: title=${fallbackResult.displayTitle}, id=${fallbackResult.id}, poster_path=${fallbackResult.poster_path}")
                    return fallbackResult
                }
                Log.w(TAG, "No fallback results with poster_path for '$queryWithoutYear'")
            }
            // If no poster found, return the first result (if any)
            response.results.firstOrNull()?.also {
                Log.d(TAG, "Falling back to first result without poster: title=${it.displayTitle}, id=${it.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TMDB error for query '$query': ${e.message}", e)
            null
        }
    }
    suspend fun getEpisodeData(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ): TmdbEpisode? {
        return try {
            Log.d(TAG, "Fetching episode: seriesId=$seriesId, season=$seasonNumber, episode=$episodeNumber, API_KEY=$API_KEY")
            val response = service.getEpisode(seriesId, seasonNumber, episodeNumber, API_KEY)
            if (response.name.isNullOrEmpty()) {
                Log.w(TAG, "Empty episode data received for S$seasonNumber E$episodeNumber")
                null
            } else {
                Log.d(TAG, "TMDB episode success: name=${response.name}, air_date=${response.air_date}")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch episode S$seasonNumber E$episodeNumber for seriesId=$seriesId: ${e.message}", e)
            null
        }
    }

    suspend fun getSeasonData(
        seriesId: Int,
        seasonNumber: Int
    ): TmdbSeason? {
        return try {
            Log.d(TAG, "Fetching season: seriesId=$seriesId, season=$seasonNumber")
            service.getSeason(seriesId, seasonNumber, API_KEY).also {
                Log.d(TAG, "TMDB season success: season=${it.season_number}, poster_path=${it.poster_path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch season $seasonNumber for seriesId=$seriesId: ${e.message}", e)
            null
        }
    }
}
