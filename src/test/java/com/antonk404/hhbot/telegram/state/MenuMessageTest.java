package com.antonk404.hhbot.telegram.state;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.store.InMemoryKeyValueStore;
import com.antonk404.hhbot.telegram.TelegramReplies;
import com.antonk404.hhbot.telegram.access.Access;
import com.antonk404.hhbot.telegram.view.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuMessageTest {

    private static final Screen SCREEN = new Screen("меню", null);

    private final TelegramReplies replies = mock(TelegramReplies.class);
    private final MenuMessage menu = new MenuMessage(replies, new InMemoryKeyValueStore());

    private BotUser user;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private Access.Press press(int messageId) {
        return new Access.Press(user, 100L, messageId, "q", "m:home");
    }

    @Test
    void buttonPressRedrawsInPlace() {
        when(replies.editMenu(100L, 7, SCREEN)).thenReturn(true);

        menu.edit(press(7), SCREEN, "Сохранил");

        verify(replies).answerCallback("q", "Сохранил");
        verify(replies).editMenu(100L, 7, SCREEN);
        verify(replies, never()).menu(any(), any());
    }

    @Test
    void textInputMovesTheMenuToTheBottom() {
        when(replies.editMenu(100L, 7, SCREEN)).thenReturn(true);
        when(replies.menu(100L, SCREEN)).thenReturn(8);
        menu.edit(press(7), SCREEN);

        menu.replace(user, 100L, SCREEN);

        InOrder order = inOrder(replies);
        order.verify(replies).deleteMessage(100L, 7);
        order.verify(replies).menu(100L, SCREEN);
    }

    @Test
    void updateEditsTheMenuThatReplaceJustSent() {
        when(replies.menu(100L, SCREEN)).thenReturn(8);
        when(replies.editMenu(100L, 8, SCREEN)).thenReturn(true);

        menu.replace(user, 100L, SCREEN);
        menu.update(user, 100L, SCREEN);

        verify(replies).editMenu(100L, 8, SCREEN);
        verify(replies).menu(100L, SCREEN);
    }

    /** Меню могли удалить руками. Тогда экран должен прийти новым сообщением, а не пропасть. */
    @Test
    void sendsAnewWhenTheOldMenuIsGone() {
        when(replies.editMenu(any(), anyInt(), any())).thenReturn(false);
        when(replies.menu(100L, SCREEN)).thenReturn(9);

        menu.finish(press(7), SCREEN);

        verify(replies).menu(100L, SCREEN);
    }

    @Test
    void progressAnswersThePressSoFinishDoesNotAnswerTwice() {
        when(replies.editMenu(100L, 7, SCREEN)).thenReturn(true);

        menu.progress(press(7), "Ищу…");
        menu.finish(press(7), SCREEN);

        verify(replies).answerCallback("q", null);
        verify(replies).editText(100L, 7, "⏳ Ищу…");
    }
}
