package com.gdg.scrollmanager.utils

import android.content.Context
import android.util.Log

/**
 * 디지털 중독 확률 데이터를 관리하는 유틸리티 클래스
 */
object AddictionProbabilityManager {
    private const val TAG = "AddictionProbabilityManager"
    private const val PREFS_NAME = "addiction_data"
    private const val KEY_ADDICTION_PROBABILITY = "addiction_probability"
    private const val DEFAULT_PROBABILITY = 65

    /**
     * 디지털 중독 확률 값을 저장
     *
     * @param context Context 객체
     * @param probability 저장할 확률 값 (0-100 사이의 정수)
     */
    fun setAddictionProbability(context: Context, probability: Int) {
        val validProbability = probability.coerceIn(0, 100)
        
        Log.d(TAG, "Setting addiction probability to $validProbability%")
        
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putInt(KEY_ADDICTION_PROBABILITY, validProbability)
            .apply()
    }

    /**
     * 저장된 디지털 중독 확률 값을 가져옴
     *
     * @param context Context 객체
     * @return 중독 확률 (0-100 사이의 정수)
     */
    fun getAddictionProbability(context: Context): Int {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val probability = sharedPreferences.getInt(KEY_ADDICTION_PROBABILITY, DEFAULT_PROBABILITY)
        
        Log.d(TAG, "Getting addiction probability: $probability%")
        return probability
    }

    /**
     * 사용자의 디지털 사용 패턴을 분석하여 중독 확률을 계산
     * 실제 구현에서는 다양한 지표를 기반으로 계산할 수 있음
     * 현재는 예시 구현으로 랜덤값 사용
     *
     * @param context Context 객체
     * @return 새로 계산된 중독 확률 (0-100 사이의 정수)
     */
    fun calculateAddictionProbability(context: Context): Int {
        // 실제 앱에서는 여러 사용 패턴 지표를 분석하여 계산
        // 여기서는 예시로 랜덤 확률 생성 (50-85 사이)
        val newProbability = (50..85).random()
        
        // 계산된 값 저장
        setAddictionProbability(context, newProbability)
        
        Log.d(TAG, "Calculated new addiction probability: $newProbability%")
        return newProbability
    }
}