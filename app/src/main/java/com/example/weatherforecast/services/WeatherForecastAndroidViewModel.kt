package com.example.weatherforecast.services

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.weatherforecast.data.remote.WeatherApiService
import com.example.weatherforecast.data.remote.data.WeatherDetailsContent
import com.example.weatherforecast.enums.RequestType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.time.Instant


class WeatherForecastAndroidViewModel(application: Application) : AndroidViewModel(application) {
    private val filenameDefaultLocation = "defaultLocation.json"
    private val filenameFavouriteLocations = "favouriteLocations.json"
    private val apiService = WeatherApiService.create()
    private val listener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "units" || key == "defaultLocation") {
                refreshLocationsForecasts()
            }
        }
    private var favouritesToBeUpdated = 0
    val settings: SettingsService = SettingsService(application)
    var defaultLocation = MutableLiveData<WeatherDetailsContent>()
    var currentSearchLocation = MutableLiveData<WeatherDetailsContent>()
    var favouriteLocationsForecast = MutableLiveData<List<WeatherDetailsContent>>()
    var favouritesUpdatingInProgress = MutableLiveData<Boolean>()

    init {
        defaultLocation.value = takeDefaultLocationDataFromStorage()
        currentSearchLocation.value = defaultLocation.value
        favouriteLocationsForecast.value = takeFavouriteLocationsDataFromStorage()
        favouritesUpdatingInProgress.value = false
        settings.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun saveDataToPrivateStorage() {
        saveDefaultLocationDataToStorage()
        saveFavouriteLocationsDataToStorage()
    }

    private fun saveDefaultLocationDataToStorage() {
        if (defaultLocation.value != null) {
            try {
                val gson = Gson()
                val locationsForecast = gson.toJson(defaultLocation.value)
                val fileOutputStream = getApplication<Application>().openFileOutput(
                    filenameDefaultLocation,
                    MODE_PRIVATE
                )
                fileOutputStream.write(locationsForecast.toByteArray())
            } catch (e: Exception) {
                displayNotification("Save to the private storage failed!")
            }
        }
    }

    private fun saveFavouriteLocationsDataToStorage() {
        try {
            val gson = Gson()
            val locationsForecast = gson.toJson(favouriteLocationsForecast.value)
            val fileOutputStream = getApplication<Application>().openFileOutput(
                filenameFavouriteLocations,
                MODE_PRIVATE
            )
            fileOutputStream.write(locationsForecast.toByteArray())
        } catch (e: Exception) {
            displayNotification("Save to the private storage failed!")
        }
    }

    private fun takeDefaultLocationDataFromStorage(): WeatherDetailsContent? {
        return try {
            val fileInputStream: FileInputStream? =
                getApplication<Application>().openFileInput(filenameDefaultLocation)
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            val stringBuilder: StringBuilder = StringBuilder()
            var text: String? = null
            while (run {
                    text = bufferedReader.readLine()
                    text
                } != null) {
                stringBuilder.append(text)
            }
            val gson = Gson()
            return gson.fromJson<WeatherDetailsContent>(
                stringBuilder.toString(),
                WeatherDetailsContent::class.java
            )
        } catch (e: Exception) {
            displayNotification("Fetch of locations from the private storage failed!")
            null
        }
    }

    private fun takeFavouriteLocationsDataFromStorage(): List<WeatherDetailsContent> {
        return try {
            val fileInputStream: FileInputStream? =
                getApplication<Application>().openFileInput(filenameFavouriteLocations)
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            val stringBuilder: StringBuilder = StringBuilder()
            var text: String? = null
            while (run {
                    text = bufferedReader.readLine()
                    text
                } != null) {
                stringBuilder.append(text)
            }
            val gson = Gson()
            val itemType = object : TypeToken<List<WeatherDetailsContent>>() {}.type
            return gson.fromJson<List<WeatherDetailsContent>>(stringBuilder.toString(), itemType)
        } catch (e: java.lang.Exception) {
            displayNotification("Fetch of locations from the private storage failed!")
            listOf()
        }
    }

    fun checkIfLocationInFavourites(locationName: String): Boolean {
        for (i in favouriteLocationsForecast.value?.indices!!) {
            if (favouriteLocationsForecast.value!![i].locationName == locationName) return true
        }
        return false
    }

    fun addLocationToFavourites(locationForecast: WeatherDetailsContent) {
        val filteredLocations: List<WeatherDetailsContent>? =
            favouriteLocationsForecast.value?.filter { item -> item.locationName != locationForecast.locationName }
        val mutableListLocations = filteredLocations?.toMutableList()
        mutableListLocations?.add(locationForecast)
        favouriteLocationsForecast.value = mutableListLocations?.toList()
    }

    fun removeLocationFromFavourites(locationForecast: WeatherDetailsContent) {
        favouriteLocationsForecast.value =
            favouriteLocationsForecast.value?.filter { item -> item.locationName != locationForecast.locationName }
    }

    fun searchLocationForecast(location: String, requestType: RequestType) {
        var locationName = location
        val units = settings.units
        MainScope().launch {
            kotlin.runCatching {
                apiService.getCoordinates(location)
            }.onSuccess {
                if (!it.isNullOrEmpty()) {
                    kotlin.runCatching {
                        locationName = it[0].name
                        apiService.getForecast(it[0].lat, it[0].lon, units)
                    }.onSuccess { it2 ->
                        if (it2 != null) {
                            val weatherDetails =
                                WeatherDetailsContent(it2, locationName, units)
                            when (requestType) {
                                RequestType.DEFAULT -> {
                                    if (defaultLocation.value != null
                                        && currentSearchLocation.value != null
                                        && defaultLocation.value!!.locationName == currentSearchLocation.value!!.locationName) {
                                        currentSearchLocation.value = weatherDetails
                                    }
                                    defaultLocation.value = weatherDetails
                                }
                                RequestType.SEARCH -> {
                                    currentSearchLocation.value = weatherDetails
                                }
                                else -> {
                                    addLocationToFavourites(weatherDetails)
                                    updateFavouritesUpdateState()
                                }
                            }
                        } else {
                            displayNotification("Fetching location forecast failed!")
                            updateFavouritesUpdateState()
                        }
                    }.onFailure {
                        displayNotification("Fetching location forecast failed!")
                        updateFavouritesUpdateState()
                    }
                } else {
                    displayNotification("Fetching location forecast failed!")
                    updateFavouritesUpdateState()
                }
            }.onFailure {
                displayNotification("Fetching location forecast failed!")
                updateFavouritesUpdateState()
            }
        }
    }

    private fun updateFavouritesUpdateState() {
        favouritesToBeUpdated -= 1
        if (favouritesToBeUpdated <= 0) {
            favouritesToBeUpdated = 0
            favouritesUpdatingInProgress.value = false
        }
    }

    fun refreshLocationsForecasts() {
        searchLocationForecast(settings.defaultLocation, RequestType.DEFAULT)
        if (currentSearchLocation.value != null && currentSearchLocation.value!!.locationName != settings.defaultLocation) {
            searchLocationForecast(currentSearchLocation.value!!.locationName, RequestType.SEARCH)
        }
        if (favouritesUpdatingInProgress.value == false
            && favouriteLocationsForecast.value != null
            && favouriteLocationsForecast.value!!.isNotEmpty()
        ) {
            favouritesUpdatingInProgress.value = true
            favouritesToBeUpdated = favouriteLocationsForecast.value!!.size
            for (index in favouriteLocationsForecast.value!!.indices) {
                searchLocationForecast(
                    favouriteLocationsForecast.value!![index].locationName,
                    RequestType.FAVOURITE
                )
            }
        }
    }

    fun refreshLocationsForecastsIfAutoRefreshEnabled() {
        if (settings.syncAutomatically) {
            if (favouriteLocationsForecast.value != null && favouriteLocationsForecast.value!!.isNotEmpty()) {
                val currentTimestamp = Instant.now().epochSecond
                if (favouriteLocationsForecast.value!![0].forecast.hourly[0].dt + settings.refreshAfterPeriod < currentTimestamp) {
                    refreshLocationsForecasts()
                }
            }
        }
    }

    fun displayNotification(message: String) {
        if (settings.showNotifications) {
            Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
        }
    }
}
