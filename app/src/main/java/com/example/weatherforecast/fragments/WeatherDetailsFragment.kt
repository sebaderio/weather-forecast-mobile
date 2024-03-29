package com.example.weatherforecast.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.weatherforecast.R
import com.example.weatherforecast.services.WeatherForecastAndroidViewModel
import com.example.weatherforecast.data.remote.data.HourForecast
import com.example.weatherforecast.data.remote.data.WeatherDetailsContent
import com.example.weatherforecast.databinding.FragmentWeatherDetailsBinding
import com.squareup.picasso.Picasso
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class WeatherDetailsFragment : Fragment() {
    private val weatherIconBaseUrl = "https://openweathermap.org/img/wn/"
    private val weatherForecastKey = "weatherForecast"
    private var _binding: FragmentWeatherDetailsBinding? = null
    private val binding get() = _binding!!
    var weatherForecastData: WeatherDetailsContent? = null
    private lateinit var androidViewModel: WeatherForecastAndroidViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        androidViewModel =
            ViewModelProvider(requireActivity())[WeatherForecastAndroidViewModel::class.java]
        _binding = FragmentWeatherDetailsBinding.inflate(inflater, container, false)
        configureBindingListeners()
        return binding.root
    }

    private fun configureBindingListeners() {
        binding.favouriteButton.setOnClickListener {
            if (weatherForecastData != null) {
                if (binding.favouriteButton.tag == "full") {
                    setOutlinedStarButton()
                    androidViewModel.removeLocationFromFavourites(weatherForecastData!!)
                    removeFromFavouriteLocationsInAdapter()
                } else {
                    setFullStarButton()
                    androidViewModel.addLocationToFavourites(weatherForecastData!!)
                }
            }
        }
    }

    private fun removeFromFavouriteLocationsInAdapter() {
        val favouritesPager: ViewPager2? = activity?.findViewById(R.id.favouritesPager)
        if (favouritesPager != null) {
            (favouritesPager.adapter as FavouritesAdapter).removeFragment(weatherForecastData!!.locationName)
        }
    }

    private fun setFullStarButton() {
        binding.favouriteButton.setImageResource(R.drawable.ic_baseline_star_100)
        binding.favouriteButton.tag = "full"
    }

    private fun setOutlinedStarButton() {
        binding.favouriteButton.setImageResource(R.drawable.ic_baseline_star_outline_100)
        binding.favouriteButton.tag = "outline"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        weatherForecastData?.let { updateFragmentContent(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (weatherForecastData != null) {
            val data = Json.encodeToString(weatherForecastData)
            outState.putString(weatherForecastKey, data)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            val stringData = savedInstanceState.getString(weatherForecastKey)
            if (stringData != null && stringData.isNotEmpty()) {
                weatherForecastData = Json.decodeFromString<WeatherDetailsContent>(stringData)
                if (weatherForecastData != null) {
                    updateFragmentContent(weatherForecastData!!)
                }
            }
        }
    }

    fun updateFragmentContent(weatherDetails: WeatherDetailsContent) {
        if (weatherDetails.forecast.hourly.size < 25) {
            androidViewModel.displayNotification("Failed to load the location forecast!")
            return
        }
        weatherForecastData = weatherDetails
        binding.cityName.text = weatherDetails.locationName
        setTemperature(weatherDetails.forecast.hourly[0].temp, weatherDetails.units)
        setImage(weatherDetails.forecast.hourly[0].weather[0].icon)
        binding.weatherType.text = weatherDetails.forecast.hourly[0].weather[0].main
        updateHourlyForecast(
            weatherDetails.forecast.hourly.subList(1, 25),
            weatherDetails.forecast.timezone_offset,
            weatherDetails.units
        )
        if (androidViewModel.checkIfLocationInFavourites(weatherDetails.locationName)) {
            setFullStarButton()
        } else {
            setOutlinedStarButton()
        }
    }

    private fun setTemperature(temperature: Float, units: String) {
        val roundedTemp = (temperature * 10).toInt().toFloat() / 10
        when (units) {
            "imperial" -> ("${roundedTemp}F").also { binding.temperature.text = it }
            "metric" -> ("${roundedTemp}°C").also { binding.temperature.text = it }
            else -> ("${roundedTemp}K").also { binding.temperature.text = it }
        }
    }

    private fun setImage(imageName: String) {
        val url = "$weatherIconBaseUrl$imageName@2x.png"
        Picasso.get().load(url).into(binding.imageView)
    }

    private fun updateHourlyForecast(
        hourlyWeatherDetails: List<HourForecast>,
        timezoneOffset: Int,
        units: String
    ) {
        for (index in hourlyWeatherDetails.indices) {
            val id: Int = resources.getIdentifier(
                "fragmentContainerView" + (index + 1).toString(),
                "id",
                requireActivity().packageName
            )
            val fragment: WeatherHourDetailsFragment =
                childFragmentManager.findFragmentById(id) as WeatherHourDetailsFragment
            fragment.updateFragmentContent(hourlyWeatherDetails[index], timezoneOffset, units)
        }
    }
}
