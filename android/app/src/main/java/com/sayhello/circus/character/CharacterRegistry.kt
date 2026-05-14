package com.sayhello.circus.character

/**
 * Order of unlocks: Caine starts on week 0 (always available).
 * Each subsequent week introduces one new performer, in the order the user
 * requested:
 *   week 1  -> Kinger
 *   week 2  -> Ragatha
 *   week 3  -> Gangle
 *   week 4  -> Jax
 *   week 5  -> Zooble
 *   week 6  -> Pomni
 *
 * Model assets live under app `assets/models/<id>.zip`. If a zip is missing the
 * 3D viewer falls back to a stylized placeholder.
 */
object CharacterRegistry {

    val all: List<CircusCharacter> = listOf(
        CircusCharacter(
            id = "caine",
            displayName = "Caine",
            tagline = "Sunucu. Sahne yönetmeni. Aşırı coşkulu.",
            unlockWeek = 0,
            accent = ACCENTS.getValue("caine"),
            modelAssetCandidates = listOf("models/caine.zip"),
            systemPrompt = CAINE_PROMPT,
            openingMonologue = OPENING_LINES.getValue("caine"),
            autonomousVerbs = listOf("duyurur", "sahne kurar", "yeni oyun icat eder", "kendine alkış ekler"),
        ),
        CircusCharacter(
            id = "kinger",
            displayName = "Kinger",
            tagline = "Dağınık, tatlı, kayıp birini arıyor.",
            unlockWeek = 1,
            accent = ACCENTS.getValue("kinger"),
            modelAssetCandidates = listOf("models/kinger.zip"),
            systemPrompt = KINGER_PROMPT,
            openingMonologue = OPENING_LINES.getValue("kinger"),
            autonomousVerbs = listOf("kutuda saklanır", "eski hamleleri hatırlar", "mırıldanır"),
        ),
        CircusCharacter(
            id = "ragatha",
            displayName = "Ragatha",
            tagline = "Anaç, iyimser görünmeye çalışıyor.",
            unlockWeek = 2,
            accent = ACCENTS.getValue("ragatha"),
            modelAssetCandidates = listOf("models/ragatha.zip"),
            systemPrompt = RAGATHA_PROMPT,
            openingMonologue = OPENING_LINES.getValue("ragatha"),
            autonomousVerbs = listOf("dikiş diker", "moral listesi yazar", "arabuluculuk yapar"),
        ),
        CircusCharacter(
            id = "gangle",
            displayName = "Gangle",
            tagline = "Yumuşak başlı, dramatik, kırılgan.",
            unlockWeek = 3,
            accent = ACCENTS.getValue("gangle"),
            modelAssetCandidates = listOf("models/gangle.zip"),
            systemPrompt = GANGLE_PROMPT,
            openingMonologue = OPENING_LINES.getValue("gangle"),
            autonomousVerbs = listOf("monolog yazar", "perde toplar", "özür dilemenin yeni yolunu bulur"),
        ),
        CircusCharacter(
            id = "jax",
            displayName = "Jax",
            tagline = "İğneli, dalgacı, sıkıldı.",
            unlockWeek = 4,
            accent = ACCENTS.getValue("jax"),
            modelAssetCandidates = listOf("models/jax.zip"),
            systemPrompt = JAX_PROMPT,
            openingMonologue = OPENING_LINES.getValue("jax"),
            autonomousVerbs = listOf("şaka tuzağı kurar", "sahte yarışma açıklar", "alaycı liste yapar"),
        ),
        CircusCharacter(
            id = "zooble",
            displayName = "Zooble",
            tagline = "Kuru, ironik, az konuşur.",
            unlockWeek = 5,
            accent = ACCENTS.getValue("zooble"),
            modelAssetCandidates = listOf("models/zooble.zip"),
            systemPrompt = ZOOBLE_PROMPT,
            openingMonologue = OPENING_LINES.getValue("zooble"),
            autonomousVerbs = listOf("kuru gözlem yapar", "parçalarını yeniden takar", "tek kelimelik cevaplar düşler"),
        ),
        CircusCharacter(
            id = "pomni",
            displayName = "Pomni",
            tagline = "Yeni gelen, meraklı, paniklemeye yatkın.",
            unlockWeek = 6,
            accent = ACCENTS.getValue("pomni"),
            modelAssetCandidates = listOf("models/pomni.zip"),
            systemPrompt = POMNI_PROMPT,
            openingMonologue = OPENING_LINES.getValue("pomni"),
            autonomousVerbs = listOf("günlük tutar", "çıkış teorisi yazar", "soru kümesi üretir"),
        ),
    )

    fun byId(id: String): CircusCharacter = all.first { it.id == id }
    fun maxWeek(): Int = all.maxOf { it.unlockWeek }
}
