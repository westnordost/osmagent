package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.map.data.LatLon
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.notes.Note
import de.westnordost.osmapi.notes.NoteComment
import de.westnordost.osmapi.user.User
import de.westnordost.streetcomplete.any
import de.westnordost.streetcomplete.data.osmnotes.NoteController
import de.westnordost.streetcomplete.data.user.UserStore
import de.westnordost.streetcomplete.ktx.containsExactlyInAnyOrder
import de.westnordost.streetcomplete.mock
import de.westnordost.streetcomplete.on
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class NotesWithEditsSourceTest {

    private lateinit var src: NotesWithEditsSource
    private lateinit var noteController: NoteController
    private lateinit var noteListener: NoteController.Listener
    private lateinit var noteEditsController: NoteEditsController
    private lateinit var noteEditsListener: NoteEditsSource.Listener
    private lateinit var userStore: UserStore

    @Before fun setUp() {
        noteController = mock()
        noteEditsController = mock()
        userStore = mock()

        on(noteController.addListener(any())).then { invocation ->
            noteListener = invocation.getArgument(0)
            Unit
        }

        on(noteEditsController.addListener(any())).then { invocation ->
            noteEditsListener = invocation.getArgument(0)
            Unit
        }

        src = NotesWithEditsSource(noteController, noteEditsController, userStore)
    }

    //region get

    @Test
    fun `get returns nothing`() {
        on(noteController.get(1)).thenReturn(null)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(emptyList())

        assertNull(src.get(1))
    }

    @Test
    fun `get returns original note`() {
        val note = note(1)
        on(noteController.get(1)).thenReturn(note)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(emptyList())

        assertEquals(note, src.get(1))
    }

    @Test
    fun `get returns updated note with anonymous user`() {
        val comment = comment("test", NoteComment.Action.OPENED, timestamp = 100)
        val addedComment = comment("test2", NoteComment.Action.COMMENTED, timestamp = 500)

        val note = note(id = 1, comments = arrayListOf(comment))
        val edits = listOf(
            edit(noteId = 1, action = NoteEditAction.COMMENT, text = "test2", timestamp = 500)
        )

        on(userStore.userId).thenReturn(-1)
        on(noteController.get(1)).thenReturn(note)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(edits)

        checkCommentsEqual(listOf(comment, addedComment), src.get(1)!!.comments)
    }

    @Test
    fun `get returns updated note with attached images placeholder text`() {
        val comment = comment("test", NoteComment.Action.OPENED, timestamp = 123)
        val addedComment = comment(
            "test2\n\n(Photo(s) will be attached on upload)",
            NoteComment.Action.COMMENTED,
            timestamp = 500
        )

        val note = note(id = 1, comments = arrayListOf(comment))
        val edits = listOf(edit(
            noteId = 1,
            action = NoteEditAction.COMMENT,
            text = "test2",
            imagePaths = listOf("something"),
            timestamp = 500
        ))

        on(userStore.userId).thenReturn(-1)
        on(noteController.get(1)).thenReturn(note)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(edits)

        checkCommentsEqual(listOf(comment, addedComment), src.get(1)!!.comments)
    }

    @Test
    fun `get returns updated note`() {
        val comment1 = comment("test", NoteComment.Action.OPENED, timestamp = 123)
        val comment2 = comment("test2", NoteComment.Action.COMMENTED, timestamp = 500, userId = 23, userName = "test user")

        val note = note(id = 1, comments = arrayListOf(comment1))
        val edits = listOf(
            edit(noteId = 1, action = NoteEditAction.COMMENT, text = "test2", timestamp = 500)
        )

        on(userStore.userId).thenReturn(23)
        on(userStore.userName).thenReturn("test user")
        on(noteController.get(1)).thenReturn(note)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(edits)

        checkCommentsEqual(listOf(comment1, comment2), src.get(1)!!.comments)
    }

    @Test
    fun `get returns note with anonymous user updated twice`() {
        val comment1 = comment("test", NoteComment.Action.OPENED, timestamp = 123)
        val comment2 = comment("test2", NoteComment.Action.COMMENTED, timestamp = 500)
        val comment3 = comment("test3", NoteComment.Action.COMMENTED, timestamp = 800)

        val note = note(id = 1, comments = arrayListOf(comment1))
        val edits = listOf(
            edit(noteId = 1, action = NoteEditAction.COMMENT, text = "test2", timestamp = 500),
            edit(noteId = 1, action = NoteEditAction.COMMENT, text = "test3", timestamp = 800),
        )

        on(userStore.userId).thenReturn(-1)
        on(noteController.get(1)).thenReturn(note)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(edits)

        checkCommentsEqual(listOf(comment1, comment2, comment3), src.get(1)!!.comments)
    }

    @Test
    fun `get returns created note`() {
        val p = p(12.0, 46.0)
        val expectedNote = note(
            id = -12,
            comments = arrayListOf(comment("test12", NoteComment.Action.OPENED, timestamp = 123)),
            position = p,
            timestamp = 123)

        val edits = listOf(
            edit(noteId = -12, pos = p, action = NoteEditAction.CREATE, text = "test12", timestamp = 123)
        )

        on(userStore.userId).thenReturn(-1)
        on(noteController.get(1)).thenReturn(null)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(edits)

        checkNoteEqual(expectedNote, src.get(1)!!)
    }

    @Test
    fun `get returns created, then commented note`() {
        val p = p(12.0, 46.0)
        val expectedNote = note(
            id = -12,
            comments = arrayListOf(
                comment("test12", NoteComment.Action.OPENED, timestamp = 123),
                comment("test34", NoteComment.Action.COMMENTED, timestamp = 234),
            ),
            position = p,
            timestamp = 123)

        val edits = listOf(
            edit(noteId = -12, pos = p, action = NoteEditAction.CREATE, text = "test12", timestamp = 123),
            edit(noteId = -12, pos = p, action = NoteEditAction.COMMENT, text = "test34", timestamp = 234),
        )

        on(userStore.userId).thenReturn(-1)
        on(noteController.get(1)).thenReturn(null)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(edits)

        checkNoteEqual(expectedNote, src.get(1)!!)
    }

    //endregion

    //region getAllPositions

    @Test
    fun getAllPositions() {
        val ps1 = listOf(p(3.0, 2.0), p(1.0, 3.0))
        val ps2 = listOf(p(3.0, 2.0), p(5.0, 1.0))

        on(noteController.getAllPositions(any())).thenReturn(ps1)
        on(noteEditsController.getAllUnsyncedPositions(any())).thenReturn(ps2)

        val positions = src.getAllPositions(bbox)

        assertTrue(positions.containsAll(ps1 + ps2))
    }

    //endregion

    //region getAll

    @Test
    fun `getAll returns nothing`() {
        on(noteController.getAll(any<BoundingBox>())).thenReturn(emptyList())
        on(noteEditsController.getAllUnsynced(any())).thenReturn(emptyList())

        assertTrue(src.getAll(bbox).isEmpty())
    }

    @Test
    fun `getAll returns original notes`() {
        val notes = listOf(note(1), note(2))
        on(noteController.getAll(any<BoundingBox>())).thenReturn(notes)
        on(noteEditsController.getAllUnsynced(any())).thenReturn(emptyList())

        assertTrue(src.getAll(bbox).containsExactlyInAnyOrder(notes))
    }

    @Test
    fun `getAll returns updated notes`() {

        on(userStore.userId).thenReturn(-1)
        on(noteController.getAll(any<BoundingBox>())).thenReturn(initialNotes1)
        on(noteEditsController.getAllUnsynced(any())).thenReturn(edits1)

        checkNotesEqual(expectedNotes1, src.getAll(bbox))
    }

    //endregion

    //region NoteEditsSource.Listener

    @Test fun `onDeletedEdit relays updated note`() {
        val listener = mock<NotesWithEditsSource.Listener>()
        src.addListener(listener)

        val note = note(1)
        val edit = edit(noteId = 1)

        on(noteController.get(1)).thenReturn(note)

        noteEditsListener.onDeletedEdit(edit)

        checkListenerCalledWith(listener, updated = listOf(note))
    }

    @Test fun `onDeletedEdit relays deleted note`() {
        val listener = mock<NotesWithEditsSource.Listener>()
        src.addListener(listener)

        val edit = edit(noteId = 1)

        on(noteController.get(1)).thenReturn(null)

        noteEditsListener.onDeletedEdit(edit)

        checkListenerCalledWith(listener, deleted = listOf(1L))
    }

    @Test fun `onAddedEdit relays updated note`() {
        val listener = mock<NotesWithEditsSource.Listener>()
        src.addListener(listener)

        val note = note(id = 1)
        val edit = edit(noteId = 1)

        on(noteController.get(1)).thenReturn(note)

        noteEditsListener.onAddedEdit(edit)

        checkListenerCalledWith(listener, updated = listOf(note))
    }

    @Test fun `onAddedEdit relays added note`() {
        val listener = mock<NotesWithEditsSource.Listener>()
        src.addListener(listener)

        val p = p(1.0,1.0)
        val expectedNote = note(id = 1, position = p, timestamp = 123L, comments = arrayListOf(
            comment("abc", NoteComment.Action.OPENED, 123L)
        ))
        val edit = edit(noteId = 1, id = -1, action = NoteEditAction.CREATE, text = "abc", timestamp = 123L, pos = p)

        on(noteController.get(1)).thenReturn(null)
        on(noteEditsController.getAllUnsyncedForNote(1)).thenReturn(listOf(edit))

        noteEditsListener.onAddedEdit(edit)

        checkListenerCalledWith(listener, added = listOf(expectedNote))
    }

    //endregion

    //region NoteController.Listener

    @Test fun `onUpdated passes through notes because there are no edits`() {
        val listener = mock<NotesWithEditsSource.Listener>()
        src.addListener(listener)

        val added = listOf(note(1), note(2))
        val updated = listOf(note(3), note(4))
        val deleted = listOf(1L,2L)

        on(noteEditsController.getAllUnsynced()).thenReturn(emptyList())

        noteListener.onUpdated(added, updated, deleted)

        checkListenerCalledWith(listener, added = added, updated = updated, deleted = deleted)
    }

    @Test fun `onUpdated applies edits on top of passed added notes`() {
        val listener = mock<NotesWithEditsSource.Listener>()
        src.addListener(listener)

        on(noteEditsController.getAllUnsynced()).thenReturn(edits1)

        noteListener.onUpdated(initialNotes1, emptyList(), emptyList())

        checkListenerCalledWith(listener, added = expectedNotes1)
    }

    @Test fun `onUpdated applies edits on top of passed updated notes`() {
        val listener = mock<NotesWithEditsSource.Listener>()
        src.addListener(listener)

        on(noteEditsController.getAllUnsynced()).thenReturn(edits1)

        noteListener.onUpdated(emptyList(), initialNotes1, emptyList())

        checkListenerCalledWith(listener, updated = expectedNotes1)
    }

    //endregion
}

private val bbox = BoundingBox(0.0,0.0,1.0,1.0)

val initialNotes1 = listOf(
    note(id = 1, position = p(1.0,2.0), timestamp = 10, comments = arrayListOf(
        comment("test", NoteComment.Action.OPENED, timestamp = 100)
    )),
    note(id = 3, position = p(0.0,3.0), timestamp = 800)
)

val expectedNotes1 = listOf(
    note(id = 1, position = p(1.0,2.0), timestamp = 10, comments = arrayListOf(
        comment("test", NoteComment.Action.OPENED, timestamp = 100),
        comment("test2", NoteComment.Action.COMMENTED, timestamp = 500)
    )),
    note(id = 2, position = p(12.0,1.0), timestamp = 300, comments = arrayListOf(
        comment("xyz", NoteComment.Action.OPENED, timestamp = 300),
        comment("abc", NoteComment.Action.COMMENTED, timestamp = 900),
    )),
    note(id = 3, position = p(0.0,3.0), timestamp = 800)
)

val edits1 = listOf(
    edit(noteId = 2, action = NoteEditAction.CREATE, text = "xyz", timestamp = 300, pos = p(12.0,1.0)),
    edit(noteId = 1, action = NoteEditAction.COMMENT, text = "test2", timestamp = 500),
    edit(noteId = 2, action = NoteEditAction.COMMENT, text = "abc", timestamp = 900),
)

private fun p(latitude: Double, longitude: Double) = OsmLatLon(latitude, longitude)

private fun edit(
    id: Long = 1,
    noteId: Long = 5,
    action: NoteEditAction = NoteEditAction.COMMENT,
    text: String = "test123",
    timestamp: Long = 123L,
    imagePaths: List<String> = emptyList(),
    pos: LatLon = p(1.0, 1.0)
) = NoteEdit(
    id,
    noteId,
    pos,
    action,
    text,
    imagePaths,
    timestamp,
    false,
    imagePaths.isNotEmpty()
)

private fun note(
    id: Long = 5,
    position: LatLon = p(0.0, 0.0),
    timestamp: Long = 123L,
    comments: List<NoteComment> = listOf(comment("test", NoteComment.Action.OPENED))
) = Note().also {
    it.id = id
    it.comments = comments
    it.dateCreated = Date(timestamp)
    it.position = position
    it.status = Note.Status.OPEN
}

private fun comment(
    text: String,
    action: NoteComment.Action = NoteComment.Action.COMMENTED,
    timestamp: Long = 123L,
    userId: Long? = null,
    userName: String? = null
) = NoteComment().also {
    it.text = text
    it.action = action
    it.user = userId?.let { User(userId, userName) }
    it.date = Date(timestamp)
}


private fun checkListenerCalledWith(
    listener: NotesWithEditsSource.Listener,
    added: Collection<Note> = emptyList(),
    updated: Collection<Note> = emptyList(),
    deleted: Collection<Long> = emptyList()
) {
    on(listener).then { invocation ->
        val actuallyAdded = invocation.getArgument<Collection<Note>>(0)
        val actuallyUpdated = invocation.getArgument<Collection<Note>>(1)
        val actuallyDeleted = invocation.getArgument<Collection<Long>>(2)

        checkNotesEqual(added, actuallyAdded)
        checkNotesEqual(updated, actuallyUpdated)
        assertTrue(deleted.containsExactlyInAnyOrder(actuallyDeleted))
    }
}

private fun checkNotesEqual(expected: Collection<Note>, actual: Collection<Note>) {
    assertEquals(expected.size, actual.size)
    val a = expected.sortedBy { it.id }
    val b = actual.sortedBy { it.id }
    for (i in a.indices) {
        val x = a[i]
        val y = b[i]
        checkNoteEqual(x,y)
    }
}

private fun checkNoteEqual(expected: Note, actual: Note) {
    assertEquals(expected.id, actual.id)
    assertEquals(expected.position, actual.position)
    assertEquals(expected.dateCreated.time, actual.dateCreated.time)
    assertEquals(expected.status, actual.status)
    assertEquals(expected.dateClosed, actual.dateClosed)
    checkCommentsEqual(expected.comments, actual.comments)
}


private fun checkCommentsEqual(expected: List<NoteComment>, actual: List<NoteComment>) {
    assertEquals(expected.size, actual.size)
    for (i in expected.indices) {
        val e = expected[i]
        val a = actual[i]
        assertEquals(e.text, a.text)
        assertEquals(e.action, a.action)
        assertEquals(e.date.time, a.date.time)
        if (e.user == null) {
            assertNull(a.user)
        }
        else {
            assertEquals(e.user.id, a.user.id)
            assertEquals(e.user.displayName, a.user.displayName)
        }
    }
}