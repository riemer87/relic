package de.riemer87.relic.storage

/** Verständliche Anzeige des gewählten Ordners, z. B. „Interner Speicher › Documents › Relic“. */
object FolderLabel {

    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** Wo der Ordner liegt: auf dem internen Speicher, einer Speicherkarte oder bei einem anderen Anbieter. */
    enum class Location { INTERNAL, SD_CARD, OTHER }

    data class Parts(val location: Location, val segments: List<String>)

    /**
     * Zerlegt die Ordner-Kennung aus der Ordnerauswahl.
     * Beim Gerätespeicher sieht sie aus wie `primary:Documents/Relic` (oder `1A2B-3C4D:Fotos` für eine
     * Speicherkarte); bei anderen Anbietern ist sie oft unlesbar – dann bleibt nur der letzte Teil.
     */
    fun parse(authority: String, treeDocumentId: String): Parts {
        if (authority != EXTERNAL_STORAGE_AUTHORITY) {
            val last = treeDocumentId.substringAfterLast('/').substringAfterLast(':').takeIf { it.isNotBlank() }
            return Parts(Location.OTHER, listOfNotNull(last))
        }
        val volume = treeDocumentId.substringBefore(':')
        val path = treeDocumentId.substringAfter(':', "")
        val segments = path.split('/').filter { it.isNotBlank() }
        return Parts(if (volume == "primary") Location.INTERNAL else Location.SD_CARD, segments)
    }
}
