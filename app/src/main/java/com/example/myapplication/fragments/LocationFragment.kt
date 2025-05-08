package com.example.myapplication.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentLocationBinding

class LocationFragment : Fragment() {
    private var _binding: FragmentLocationBinding? = null
    private val binding get() = _binding!!
    
    private var currentLocation: Location? = null
    private var locationManager: LocationManager? = null
    private lateinit var locationListener: LocationListener
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (granted) {
            startLocationUpdates()
        }
        updateUI()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = location
                updateUI()
            }
            
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        
        binding.requestPermissionButton.setOnClickListener {
            requestLocationPermission()
        }
        
        checkPermissionAndStartUpdates()
    }
    
    private fun checkPermissionAndStartUpdates() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            requestLocationPermission()
        }
        updateUI()
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
    
    private fun updateUI() {
        if (hasLocationPermission()) {
            binding.permissionLayout.visibility = View.GONE
            binding.contentLayout.visibility = View.VISIBLE
            
            currentLocation?.let { location ->
                binding.latitudeText.text = String.format("%.6f", location.latitude)
                binding.longitudeText.text = String.format("%.6f", location.longitude)
                binding.altitudeText.text = "${location.altitude.toInt()} m"
                binding.accuracyText.text = "${location.accuracy.toInt()} m"
                
                binding.loadingLayout.visibility = View.GONE
                binding.locationInfo.visibility = View.VISIBLE
            } ?: run {
                binding.loadingLayout.visibility = View.VISIBLE
                binding.locationInfo.visibility = View.GONE
            }
        } else {
            binding.permissionLayout.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }
    
    override fun onPause() {
        super.onPause()
        locationManager?.removeUpdates(locationListener)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}