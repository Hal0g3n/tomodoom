package com.example.tomodoom.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ChatHistoryWindowTest {

    @Test
    public void keepsTurnsInOrder() {
        ChatHistoryWindow w = new ChatHistoryWindow(5);
        w.addTurn("q1", "a1");
        w.addTurn("q2", "a2");
        List<ChatHistoryWindow.Message> m = w.getMessages();
        assertEquals(4, m.size());
        assertEquals(ChatHistoryWindow.ROLE_USER, m.get(0).getRole());
        assertEquals("q1", m.get(0).getContent());
        assertEquals(ChatHistoryWindow.ROLE_ASSISTANT, m.get(1).getRole());
        assertEquals("a1", m.get(1).getContent());
        assertEquals("a2", m.get(3).getContent());
    }

    @Test
    public void evictsOldestTurnBeyondWindow() {
        ChatHistoryWindow w = new ChatHistoryWindow(2);
        w.addTurn("q1", "a1");
        w.addTurn("q2", "a2");
        w.addTurn("q3", "a3");
        assertEquals(2, w.getTurnCount());
        assertEquals("q2", w.getMessages().get(0).getContent());
        assertEquals("a3", w.getMessages().get(3).getContent());
    }

    @Test
    public void windowBelowOne_keepsOneTurn() {
        ChatHistoryWindow w = new ChatHistoryWindow(0);
        w.addTurn("q1", "a1");
        w.addTurn("q2", "a2");
        assertEquals(1, w.getTurnCount());
        assertEquals("q2", w.getMessages().get(0).getContent());
    }

    @Test
    public void clear_empties() {
        ChatHistoryWindow w = new ChatHistoryWindow(3);
        w.addTurn("q", "a");
        w.clear();
        assertTrue(w.getMessages().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void messages_areUnmodifiable() {
        ChatHistoryWindow w = new ChatHistoryWindow(3);
        w.addTurn("q", "a");
        w.getMessages().clear();
    }
}
