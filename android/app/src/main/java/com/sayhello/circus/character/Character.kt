package com.sayhello.circus.character

import androidx.compose.ui.graphics.Color

/**
 * A performer in the in-app circus. All identifiers refer to the local persona file
 * the user supplies; nothing here implies endorsement by, or affiliation with, any
 * third-party rights holder. The personality prompts are written as general
 * archetype briefs — customize them in [characterPersonalities] to taste.
 */
data class CircusCharacter(
    val id: String,
    val displayName: String,
    val tagline: String,
    val unlockWeek: Int,
    val accent: Color,
    val modelAssetCandidates: List<String>,
    val systemPrompt: String,
    val openingMonologue: String,
    val autonomousVerbs: List<String>,
)
