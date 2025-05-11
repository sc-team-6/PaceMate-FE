package com.gdg.scrollmanager.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gdg.scrollmanager.databinding.FragmentInfiniteScrollBinding
import com.gdg.scrollmanager.adapters.ScrollAppAdapter
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// DataStore 확장 프로퍼티
val android.content.Context.dataStore by preferencesDataStore(name = "scroll_settings")

class InfiniteScrollFragment : Fragment() {
    private var _binding: FragmentInfiniteScrollBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ScrollAppAdapter
    private var externalScrollDistance = 0f
    private var scrollByApp = mutableMapOf<String, Float>()
    
    companion object {
        val EXTERNAL_SCROLL_KEY = floatPreferencesKey("external_scroll")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfiniteScrollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeScrollData()
        
        binding.resetButton.setOnClickListener {
            resetScrollData()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ScrollAppAdapter(requireContext())
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }
    
    private fun observeScrollData() {
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().dataStore.data.collect { preferences ->
                externalScrollDistance = preferences[EXTERNAL_SCROLL_KEY] ?: 0f
                
                scrollByApp.clear()
                preferences.asMap().filterKeys {
                    it.name.startsWith("external_scroll_")
                }.forEach { (key, value) ->
                    val packageName = key.name.removePrefix("external_scroll_")
                    scrollByApp[packageName] = (value as? Float) ?: 0f
                }
                
                updateUI()
            }
        }
    }
    
    private fun updateUI() {
        binding.totalScrollText.text = formatScrollDistance(externalScrollDistance)
        
        val scrollList = scrollByApp.entries
            .map { (packageName, distance) ->
                ScrollAppInfo(
                    packageName = packageName,
                    appName = getAppName(packageName),
                    scrollDistance = distance,
                    percentage = if (externalScrollDistance > 0) {
                        (distance / externalScrollDistance * 100).toInt()
                    } else 0
                )
            }
            .sortedByDescending { it.scrollDistance }
        
        adapter.submitList(scrollList)
    }
    
    private fun resetScrollData() {
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().dataStore.edit { prefs ->
                prefs[EXTERNAL_SCROLL_KEY] = 0f
                
                // 모든 앱별 스크롤 데이터 삭제
                prefs.asMap().keys
                    .filter { it.name.startsWith("external_scroll_") }
                    .forEach { key ->
                        prefs.remove(key)
                    }
            }
        }
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = requireContext().packageManager.getApplicationInfo(packageName, 0)
            requireContext().packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
    
    private fun formatScrollDistance(distance: Float): String {
        return when {
            distance >= 100000 -> "${String.format("%.1f", distance / 100000)}km"
            distance >= 100 -> "${String.format("%.1f", distance / 100)}m"
            else -> "${distance.toInt()}cm"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class ScrollAppInfo(
    val packageName: String,
    val appName: String,
    val scrollDistance: Float,
    val percentage: Int
)