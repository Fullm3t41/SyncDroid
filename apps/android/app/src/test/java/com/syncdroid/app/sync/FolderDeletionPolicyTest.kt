package com.syncdroid.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderDeletionPolicyTest {
    @Test fun overwriteOnlyTurnsMissingFilesIntoExceptions() {
        val plan = planMissingFiles(
            policy = FolderDeletionPolicy.OVERWRITE_ONLY,
            previousPaths = setOf("keep.sav", "deleted.sav"),
            currentPaths = setOf("keep.sav"),
            activeExceptions = emptySet(),
        )

        assertEquals(emptySet<String>(), plan.propagatedDeletions)
        assertEquals(setOf("deleted.sav"), plan.newExceptions)
    }

    @Test fun propagateModeProducesNormalDeletion() {
        val plan = planMissingFiles(
            policy = FolderDeletionPolicy.PROPAGATE,
            previousPaths = setOf("deleted.sav"),
            currentPaths = emptySet(),
            activeExceptions = emptySet(),
        )
        assertEquals(setOf("deleted.sav"), plan.propagatedDeletions)
        assertTrue(plan.newExceptions.isEmpty())
    }

    @Test fun activeExceptionNeverAppliesRemoteDeletion() {
        assertFalse(
            shouldApplyRemoteDeletion(
                FolderDeletionPolicy.PROPAGATE,
                "protected.sav",
                setOf("protected.sav"),
            ),
        )
        assertFalse(shouldApplyRemoteDeletion(FolderDeletionPolicy.OVERWRITE_ONLY, "anything.sav", emptySet()))
        assertFalse(shouldAcceptRemoteFile("protected.sav", setOf("protected.sav")))
        assertTrue(shouldAcceptRemoteFile("restored.sav", emptySet()))
    }
}
