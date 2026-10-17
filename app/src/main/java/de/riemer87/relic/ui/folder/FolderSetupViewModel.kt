package de.riemer87.relic.ui.folder

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.R
import de.riemer87.relic.journal.FolderDecision
import de.riemer87.relic.journal.FolderOption
import de.riemer87.relic.journal.JournalFolder
import de.riemer87.relic.storage.JournalLocation
import de.riemer87.relic.storage.SafDocumentTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Kurzer Hinweis, bevor sich der Android-Ordnerdialog öffnet. */
data class PickerHint(
    val initialUri: Uri?,
    /** Dokumente/Relic konnte nicht angelegt werden: Der Ordner muss selbst angelegt werden. */
    val mustCreateFolder: Boolean,
    /** „Ich habe schon ein Relic-Tagebuch“: Hinweis erklärt, wie man den vorhandenen Ordner findet. */
    val findExisting: Boolean = false,
)

/** Rückfrage nach der Auswahl, siehe [FolderDecision]. */
data class FolderQuestion(val uri: Uri, val decision: FolderDecision)

data class FolderSetupUiState(
    val hint: PickerHint? = null,
    val question: FolderQuestion? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
    /** Gesetzt, sobald ein Ordner fertig eingerichtet ist. */
    val readyUri: Uri? = null,
)

class FolderSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val location = JournalLocation(application)
    private val resolver = application.contentResolver

    private val _state = MutableStateFlow(FolderSetupUiState())
    val state: StateFlow<FolderSetupUiState> = _state.asStateFlow()

    private var option = FolderOption.RECOMMENDED

    /** Wohin der Ordnerdialog beim erneuten Öffnen springt. */
    var lastInitialUri: Uri? = null
        private set

    fun onOptionSelected(selected: FolderOption) {
        option = selected
        if (selected != FolderOption.RECOMMENDED) {
            showHint(PickerHint(initialUri = null, mustCreateFolder = false, findExisting = selected == FolderOption.EXISTING))
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) { JournalLocation.createRecommendedFolder() }
            showHint(PickerHint(JournalLocation.recommendedInitialUri(created), mustCreateFolder = !created))
        }
    }

    private fun showHint(hint: PickerHint) {
        lastInitialUri = hint.initialUri
        _state.update { it.copy(hint = hint, busy = false) }
    }

    fun hintClosed() = _state.update { it.copy(hint = null) }

    /** Ergebnis des Android-Ordnerdialogs; `null`, wenn abgebrochen. */
    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val decision = withContext(Dispatchers.IO) {
                try {
                    location.takePermission(uri)
                    JournalFolder.decide(option, JournalFolder.looksLikeJournal(SafDocumentTree(resolver, uri)))
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
            when (decision) {
                null -> fail(uri)
                FolderDecision.USE -> finish(uri)
                else -> _state.update { it.copy(busy = false, question = FolderQuestion(uri, decision)) }
            }
        }
    }

    fun confirmQuestion() {
        val question = _state.value.question ?: return
        _state.update { it.copy(question = null, busy = true) }
        viewModelScope.launch { finish(question.uri) }
    }

    /** Ein anderer Ordner ist gewünscht: Freigabe für diesen wieder abgeben. */
    fun rejectQuestion() {
        val question = _state.value.question ?: return
        location.releasePermission(question.uri)
        _state.update { it.copy(question = null) }
    }

    fun errorShown() = _state.update { it.copy(error = false) }

    private suspend fun finish(uri: Uri) {
        val app = getApplication<Application>()
        val ok = withContext(Dispatchers.IO) {
            try {
                JournalFolder.ensureReadme(
                    SafDocumentTree(resolver, uri),
                    fileName = app.getString(R.string.readme_file_name),
                    text = app.getString(R.string.readme_text),
                )
                location.save(uri)
                true
            } catch (e: IOException) {
                false
            }
        }
        if (ok) onReady(uri) else fail(uri)
    }

    internal fun onReady(uri: Uri) = _state.update { it.copy(busy = false, readyUri = uri) }

    fun readyHandled() = _state.update { it.copy(readyUri = null) }

    private fun fail(uri: Uri) {
        location.releasePermission(uri)
        _state.update { it.copy(busy = false, error = true) }
    }
}
