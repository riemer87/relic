package de.riemer87.relic.ui.entry

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.R
import de.riemer87.relic.journal.DayPhoto
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.MAX_PHOTOS
import de.riemer87.relic.journal.NewPhoto
import de.riemer87.relic.journal.SaveResult
import de.riemer87.relic.journal.StoredPhoto
import de.riemer87.relic.journal.localToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

/** Bereitet ein ausgewähltes Foto auf (siehe PhotoProcessor); `null`, wenn es nicht geht. */
fun interface PhotoPreparer {
    fun prepare(uri: Uri): File?
}

data class EntryUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val text: String = "",
    val photos: List<DayPhoto> = emptyList(),
    val mode: EntryMode = EntryMode.READ,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isAddingPhotos: Boolean = false,
    /** Meldung auf der Seite; nach dem Anzeigen über [EntryViewModel.messageShown] gelöscht. */
    val message: EntryMessage? = null,
    /** Gesetzt, sobald die Seite geschlossen werden soll; enthält die Meldung für den Zeitstrahl (oder 0). */
    @StringRes val closeWith: Int? = null,
)

/**
 * Meldung auf dem Bildschirm. Mit [number] ist [text] ein Mehrzahl-Text (plurals),
 * z. B. „Höchstens 4 Fotos pro Tag.“
 */
data class EntryMessage(val text: Int, val number: Int? = null)

/** Zustand einer Eintragsseite. Speichern beim Schließen, beim Zurückgehen und beim Verlassen der App. */
class EntryViewModel(
    private val store: JournalStore,
    date: LocalDate,
    clock: Clock,
    private val photoPreparer: PhotoPreparer = PhotoPreparer { null },
) : ViewModel() {

    private val _state = MutableStateFlow(EntryUiState(date = date, isToday = date == localToday(clock)))
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    /** Was zuletzt in der Datei stand bzw. gespeichert wurde. */
    private var savedText = ""
    private var savedPhotos: List<DayPhoto> = emptyList()

    /** Immer nur ein Speichervorgang gleichzeitig, sonst könnten zwei in dieselbe Zwischendatei schreiben. */
    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { store.load(date) }
                savedText = entry?.text.orEmpty()
                savedPhotos = entry?.photos.orEmpty().map(::StoredPhoto)
                val mode = EntryRules.initialMode(date, localToday(clock), savedText, savedPhotos.isNotEmpty())
                _state.update { it.copy(text = savedText, photos = savedPhotos, mode = mode, isLoading = false) }
            } catch (e: IOException) {
                // Bleibt gesperrt, damit eine vorhandene Datei nicht versehentlich überschrieben wird.
                _state.update { it.copy(message = EntryMessage(R.string.entry_load_failed)) }
            }
        }
    }

    fun onTextChange(text: String) {
        if (text.length <= MAX_ENTRY_LENGTH) _state.update { it.copy(text = text) }
    }

    fun edit() = _state.update { it.copy(mode = EntryMode.EDIT) }

    /** Ausgewählte Fotos aufbereiten und anhängen, höchstens bis [MAX_PHOTOS]. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val space = MAX_PHOTOS - _state.value.photos.size
        if (space <= 0) {
            _state.update { it.copy(message = EntryMessage(R.plurals.photo_limit, MAX_PHOTOS)) }
            return
        }
        _state.update { it.copy(isAddingPhotos = true) }
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) { uris.take(space).map { photoPreparer.prepare(it) } }
            val added = prepared.filterNotNull().map(::NewPhoto)
            val message = when {
                prepared.any { it == null } -> EntryMessage(R.string.photo_failed)
                uris.size > space -> EntryMessage(R.plurals.photo_limit, MAX_PHOTOS)
                else -> null
            }
            _state.update {
                it.copy(
                    photos = (it.photos + added).take(MAX_PHOTOS),
                    isAddingPhotos = false,
                    message = message ?: it.message,
                )
            }
        }
    }

    fun removePhoto(photo: DayPhoto) {
        _state.update { it.copy(photos = it.photos - photo) }
        // Noch nicht gespeicherte Fotos sofort aus dem Zwischenspeicher entfernen.
        if (photo is NewPhoto && photo !in savedPhotos) photo.file.delete()
    }

    /** „Speichern“ und „Zurück“: Änderungen sichern, dann schließen. Schlägt das fehl, bleibt die Seite offen. */
    fun close() {
        val current = _state.value
        if (current.isSaving || current.closeWith != null || current.isAddingPhotos) return
        if (current.isLoading || !hasChanges(current)) {
            _state.update { it.copy(closeWith = 0) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val message = save(current)
            _state.update {
                if (message == null) it.copy(isSaving = false, message = EntryMessage(R.string.entry_save_failed))
                else it.copy(isSaving = false, closeWith = message)
            }
        }
    }

    /** App geht in den Hintergrund: still sichern, damit nichts verloren geht, falls Android sie beendet. */
    fun saveInBackground() {
        val current = _state.value
        if (current.isLoading || current.isSaving || current.closeWith != null) return
        if (!hasChanges(current)) return
        viewModelScope.launch { save(current) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun hasChanges(state: EntryUiState) =
        EntryRules.hasChanges(state.text, savedText, state.photos, savedPhotos)

    /** Gibt die Meldung für den Bildschirm zurück, oder `null`, wenn das Speichern fehlschlug. */
    private suspend fun save(snapshot: EntryUiState): Int? = withContext(Dispatchers.IO + NonCancellable) {
        saveLock.withLock {
            try {
                val result = store.save(snapshot.date, snapshot.text, snapshot.photos)
                // Neue Fotos haben jetzt Dateinamen im Tagebuch-Ordner bekommen (gleiche Reihenfolge).
                val stored = store.load(snapshot.date)?.photos.orEmpty().map(::StoredPhoto)
                val renamed: Map<DayPhoto, DayPhoto> =
                    if (stored.size == snapshot.photos.size) snapshot.photos.zip(stored).toMap() else emptyMap()
                savedText = snapshot.text
                savedPhotos = stored
                // Auch wenn inzwischen weitere Fotos dazukamen: gespeicherte durch ihren Dateinamen ersetzen.
                _state.update { s -> s.copy(photos = s.photos.map { renamed[it] ?: it }) }
                snapshot.photos.filterIsInstance<NewPhoto>().filter { it in renamed }.forEach { it.file.delete() }
                when (result) {
                    SaveResult.SAVED -> R.string.entry_saved
                    SaveResult.DELETED -> R.string.entry_removed
                }
            } catch (e: IOException) {
                null
            }
        }
    }

    override fun onCleared() {
        // Nicht gespeicherte Fotos im Zwischenspeicher aufräumen.
        _state.value.photos.filterIsInstance<NewPhoto>().forEach { it.file.delete() }
    }
}
