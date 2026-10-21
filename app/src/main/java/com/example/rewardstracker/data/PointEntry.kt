package com.example.rewardstracker.data

// One record per fetch. epochDay is kept for reference; fetchedAt (epoch millis) is the identity.
data class PointEntry(
    val epochDay: Long,
    val available: Int,
    val lifetime: Int,
    val fetchedAt: Long
)
