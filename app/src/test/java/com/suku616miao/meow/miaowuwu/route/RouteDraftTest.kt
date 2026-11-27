package com.suku616miao.meow.miaowuwu.route

import org.junit.Assert.*
import org.junit.Test

class RouteDraftTest {
    @Test fun draggingDoesNotAddPointsAndConfirmationUsesTheCurrentCandidate() {
        val draft = RouteDraft()
        val first = RoutePoint(30.0, 120.0)
        draft.finishMapMove(first)
        assertTrue(draft.confirmCandidate())
        draft.beginMapMove()
        assertEquals(listOf(first), draft.points)
        val second = RoutePoint(30.01, 120.01)
        draft.updateCandidate(second)
        assertEquals(listOf(first), draft.points)
        assertTrue(draft.confirmCandidate())
        assertEquals(listOf(first, second), draft.points)
        draft.updateCandidate(RoutePoint(30.02, 120.02))
        assertEquals(listOf(first, second), draft.points)
    }

    @Test fun confirmingSameTargetTwiceDoesNotCreateZeroLengthSegment() {
        val draft = RouteDraft()
        draft.finishMapMove(RoutePoint(30.0, 120.0))
        assertTrue(draft.confirmCandidate())
        assertFalse(draft.confirmCandidate())
        assertEquals(1, draft.points.size)
    }

    @Test fun snapshotsStayUnchangedAfterUndoAndFurtherEditing() {
        val draft = RouteDraft()
        draft.finishMapMove(RoutePoint(30.0, 120.0))
        draft.confirmCandidate()
        val snapshot = draft.points
        draft.undo()
        assertEquals(1, snapshot.size)
        assertTrue(draft.points.isEmpty())
    }

    @Test fun wrappedLongitudeCannotAddTheSamePhysicalPositionTwice() {
        val draft = RouteDraft()
        draft.finishMapMove(RoutePoint(30.0, 180.0))
        assertTrue(draft.confirmCandidate())
        draft.finishMapMove(RoutePoint(30.0, -180.0))
        assertFalse(draft.confirmCandidate())
        assertEquals(1, draft.points.size)
    }
}
